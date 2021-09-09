package java.io

// Serializable needs special support inside dottyc compiler, when generating NIR
// it's name should be replaced to `Serializable` (similary as _String, _Class, etc )

trait _Serializable {}
