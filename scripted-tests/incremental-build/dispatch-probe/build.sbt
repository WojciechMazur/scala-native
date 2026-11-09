ThisBuild / scalaVersion := sys.props("scala.version")

// Types cross the boundaries of separately compiled projects:
//
//        core
//       /    \
//  geometry  text
//    |   \    /
//   tool   app
//
// core defines base classes and traits, geometry and text extend them
// independently, app mixes types from all three and tool reuses geometry in a
// second program. The test edits each layer and runs both programs.
lazy val core = project.enablePlugins(ScalaNativePlugin)
lazy val geometry = project.enablePlugins(ScalaNativePlugin).dependsOn(core)
lazy val text = project.enablePlugins(ScalaNativePlugin).dependsOn(core)
lazy val app = project
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(geometry, text)
lazy val tool = project.enablePlugins(ScalaNativePlugin).dependsOn(geometry)
