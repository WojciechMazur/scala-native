package java.nio.file

enum FileVisitResult extends Enum[FileVisitResult]():
  case CONTINUE extends FileVisitResult
  case TERMINATE extends FileVisitResult
  case SKIP_SUBTREE extends FileVisitResult
  case SKIP_SIBLINGS extends FileVisitResult
