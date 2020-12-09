package scala.scalanative.reporter

class ExternSyntaxReporterTest extends NirErrorReporterSpec {
  verifyThat("Fields in extern objects must have extern body") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object test {
         |  val t = 1
         |}""".stripMargin
    }
  }

  verifyThat("Methods in extern objects must have extern body") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object test {
         |  def t: Int = 1
         |}""".stripMargin
    }
  }

  verifyThat(
    "Extern objects may only have extern parents, trait foo is not extern") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object bar extends foo {
         |  var z: Int = scala.scalanative.unsafe.extern
         |}
         |trait foo {
         |}  """.stripMargin
    }
  }

  verifyThat(
    "Extern classes may only have extern parents, trait foo is not extern") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |class bar extends foo {
         |  var z: Int = scala.scalanative.unsafe.extern
         |}
         |trait foo {
         |}  """.stripMargin
    }
  }

  it should "allows to compile correct extern definition" in {
    allows {
      s"""
         |@scala.scalanative.unsafe.extern
         |object one extends two {
         |  var z: Int = scala.scalanative.unsafe.extern
         |}
         |@scala.scalanative.unsafe.extern
         |trait two {
         |}  """.stripMargin
    }
  }

  verifyThat("Fields in extern traits must have extern body") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object bar extends foo{
         |  var z: Int = scala.scalanative.unsafe.extern
         |}
         |@scala.scalanative.unsafe.extern
         |trait foo {
         |  val y: Int = 1
         |}  """.stripMargin
    }
  }

  it should "allow to compile object extending extern trait" in {
    allows {
      s"""
         |@scala.scalanative.unsafe.extern
         |object bar extends foo {
         |  var z: Int = scala.scalanative.unsafe.extern
         |}
         |@scala.scalanative.unsafe.extern
         |trait foo {
         |  val y: Int = scala.scalanative.unsafe.extern
         |}  """.stripMargin
    }
  }

  verifyThat("Fields in extern objects must not be lazy") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object test {
         |  lazy val t: Int = scala.scalanative.unsafe.extern
         |}""".stripMargin
    }
  }

  verifyThat(
    "Extern objects may only have extern parents, class Foo is not extern") {
    reportsErrors {
      s"""
         |class Foo(val x: Int)
         |@scala.scalanative.unsafe.extern
         |object Bar extends Foo(10)""".stripMargin
    }
  }

  it should "verify that not allow parameter in extern classes" in {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |class Foo(val x: Int)
         |@scala.scalanative.unsafe.extern
         |object Bar extends Foo(10)""".stripMargin
    }("Parameters in extern classes are not allowed - only extern fields and methods are allowed")
  }

  verifyThat("Extern fields in objects must have an explicit result type") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object test {
         |  val t = scala.scalanative.unsafe.extern
         |}""".stripMargin
    }
  }

  verifyThat("Extern methods in objects must have an explicit result type") {
    reportsErrors {
      s"""
         |@scala.scalanative.unsafe.extern
         |object test {
         |  def t = scala.scalanative.unsafe.extern
         |}""".stripMargin
    }
  }
}
