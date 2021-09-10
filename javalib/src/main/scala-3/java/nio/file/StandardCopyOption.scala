package java.nio.file

enum StandardCopyOption extends Enum[StandardCopyOption]() with CopyOption:
  case REPLACE_EXISTING extends StandardCopyOption
  case COPY_ATTRIBUTES extends StandardCopyOption
  case ATOMIC_MOVE extends StandardCopyOption
