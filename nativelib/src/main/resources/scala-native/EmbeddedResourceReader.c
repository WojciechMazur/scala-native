#include <stdint.h>

/* Registered by the program at startup, see codegen.Generate: the runtime may
 * be linked apart from the program and its embedded resources.
 */
static int8_t **resourcesPaths;
static int8_t **resourcesContents;
static int32_t *resourcesPathLengths;
static int32_t *resourcesContentLengths;
static int32_t resourcesAmount;

void scalanative_register_embedded_resources(int8_t **paths, int8_t **contents,
                                             int32_t *pathLengths,
                                             int32_t *contentLengths,
                                             int32_t amount) {
    resourcesPaths = paths;
    resourcesContents = contents;
    resourcesPathLengths = pathLengths;
    resourcesContentLengths = contentLengths;
    resourcesAmount = amount;
}

int8_t *scalanative_resource_get_content_ptr(int32_t embeddedFileId) {
    return resourcesContents[embeddedFileId];
}

int8_t *scalanative_resource_get_path_ptr(int32_t embeddedFileId) {
    return resourcesPaths[embeddedFileId];
}

int32_t scalanative_resource_get_embedded_size() { return resourcesAmount; }

int32_t scalanative_resource_get_path_length(int32_t embeddedFileId) {
    return resourcesPathLengths[embeddedFileId];
}

int32_t scalanative_resource_get_content_length(int32_t embeddedFileId) {
    return resourcesContentLengths[embeddedFileId];
}
