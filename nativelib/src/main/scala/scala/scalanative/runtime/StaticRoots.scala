package scala.scalanative.runtime

import scala.scalanative.unsafe._

@extern
private[runtime] object StaticRoots {
  // Register the containing static section before publishing a managed reference.
  @blocking
  @name("scalanative_register_object_roots")
  def registerObject(obj: RawPtr): Unit = extern

  // Loaded images: the executable and the shared libraries of a granular
  // build, see staticSections.h
  @name("scalanative_images_count")
  def imagesCount(): RawSize = extern

  // SNImageSections of an image, the class list section is the last of its
  // four {start, end} ranges: pointers to the RTTI of its classes and traits
  @name("scalanative_image_at")
  def imageAt(index: RawSize): RawPtr = extern
}
