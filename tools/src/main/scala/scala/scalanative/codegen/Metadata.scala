package scala.scalanative
package codegen

import scala.collection.mutable
import scalanative.nir._
import scalanative.linker.{Trait, Class}
import scala.scalanative.build.NativeConfig

class Metadata(
    val linked: linker.Result,
    val config: NativeConfig,
    proxies: Seq[Defn]
) {
  implicit private def self: Metadata = this

  val rtti = mutable.Map.empty[linker.Info, RuntimeTypeInformation]
  val vtable = mutable.Map.empty[linker.Class, VirtualTable]
  val layout = mutable.Map.empty[linker.Class, FieldLayout]
  val dynmap = mutable.Map.empty[linker.Class, DynamicHashMap]
  val ids = mutable.Map.empty[linker.ScopeInfo, Int]
  val ranges = mutable.Map.empty[linker.Class, Range]

  val classes = initClassIdsAndRanges()
  val traits = initTraitIds()
  val moduleArray = new ModuleArray(this)
  val dispatchTable = new TraitDispatchTable(this)
  val hasTraitTables = new HasTraitTables(this)

  val usesLockWord = config.multithreadingSupport
  val lockWordType = if (usesLockWord) List(Type.Ptr) else Nil
  val lockWordField = if (usesLockWord) List(Val.Null) else Nil

  val Rtti = Type.StructValue(
    Type.Ptr :: // ClassRtti
      lockWordType ::: // LockWord - ThinLock or reference to FatLock
      Type.Int :: // ClassId
      Type.Int :: // Traitid
      Type.Ptr :: // ClassName
      Nil
  )
  final val RttiClassRttiIdx = 0
  final val RttiLockWordIdx = if (usesLockWord) 1 else -1
  final val RttiClassIdIdx = if (usesLockWord) 2 else 1
  final val RttiTraitIdIdx = RttiClassIdIdx + 1
  final val RttiClassNameIdx = RttiClassIdIdx + 1

  val RttiClassIdPath = Seq(Val.Int(0), Val.Int(RttiClassIdIdx))
  val RttiTraitIdPath = Seq(Val.Int(0), Val.Int(RttiTraitIdIdx))

  // RTTI specific for classess, see class RuntimeTypeInformation
  // At index 0 there's a struct with base Rtti defined above
  final val usesDynMap = linked.dynsigs.nonEmpty
  final val RttiClassSizeIdx = 1
  final val RttiClassIdRangeIdx = 2
  final val RttiClassReferenceOffsetsIdx = 3
  final val RttiClassDynmapIdx = if (usesDynMap) 4 else -1
  final val RttiClassVtableIdx = if (usesDynMap) 5 else 4

  val RttiClassDynmapPath = Seq(Val.Int(0), Val.Int(RttiClassDynmapIdx))
  val RttiClassVtablePath = Seq(Val.Int(0), Val.Int(RttiClassVtableIdx))

  final val ObjectHeader = {
    val rtti = Type.Ptr
    rtti :: lockWordType
  }
  final val ObjectHeaderFieldsCount = ObjectHeader.size

  final val ArrayHeader = {
    val rtti = Type.Ptr
    val length = Type.Int
    val stride = Type.Int
    rtti :: lockWordType ::: length :: stride :: Nil
  }

  final val ArrayHeaderLengthIdx = Val.Int(if (usesLockWord) 2 else 1)
  final val ArrayHeaderStrideIdx = Val.Int(ArrayHeaderLengthIdx.value + 1)
  final val ArrayHeaderValuesIdx = Val.Int(ArrayHeaderStrideIdx.value + 1)

  initClassMetadata()
  initTraitMetadata()

  def initTraitIds(): Seq[Trait] = {
    val traits =
      linked.infos.valuesIterator
        .collect { case info: Trait => info }
        .toIndexedSeq
        .sortBy(_.name.show)
    traits.zipWithIndex.foreach {
      case (node, id) =>
        ids(node) = id
    }
    traits
  }

  def initClassIdsAndRanges(): Seq[Class] = {
    val out = mutable.UnrolledBuffer.empty[Class]
    var id = 0

    def loop(node: Class): Unit = {
      out += node
      val start = id
      id += 1
      val directSubclasses =
        node.subclasses.filter(_.parent == Some(node)).toArray
      directSubclasses.sortBy(_.name.show).foreach { subcls => loop(subcls) }
      val end = id - 1
      ids(node) = start
      ranges(node) = start to end
    }

    loop(linked.infos(Rt.Object.name).asInstanceOf[Class])

    out.toSeq
  }

  def initClassMetadata(): Unit = {
    classes.foreach { node =>
      vtable(node) = new VirtualTable(node)
      layout(node) = new FieldLayout(node)
      if (usesDynMap) {
        dynmap(node) = new DynamicHashMap(node, proxies)
      }
      rtti(node) = new RuntimeTypeInformation(node)
    }
  }

  def initTraitMetadata(): Unit = {
    traits.foreach { node =>
      rtti(node) = new RuntimeTypeInformation(node)
    }
  }
}
