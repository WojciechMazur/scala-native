package java.nio.file

enum LinkOption extends Enum[LinkOption]() with OpenOption with CopyOption:
  case NOFOLLOW_LINKS extends LinkOption
