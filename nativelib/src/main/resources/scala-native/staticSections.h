#ifndef SCALANATIVE_STATIC_SECTIONS_H
#define SCALANATIVE_STATIC_SECTIONS_H

#include <stddef.h>

/* A half-open range of static storage in the current linked image. */
typedef struct {
    void *start;
    void *end;
} SNStaticSection;

#ifdef _WIN32
#define SN_SECTION_LOCAL
#else
#define SN_SECTION_LOCAL __attribute__((visibility("hidden")))
#endif

/* Static sections of one linked image: the executable, or a shared library of
 * a granular build linked dynamically. Every image registers its own when
 * loaded, see staticSections.c.
 */
typedef struct {
    SNStaticSection strings;
    SNStaticSection classinfo;
    SNStaticSection globals;
    SNStaticSection classlist;
} SNImageSections;

/* Callers decide whether to register, inspect, or scan each section. */
#ifdef __cplusplus
extern "C" {
#endif
SN_SECTION_LOCAL const SNStaticSection *
scalanative_static_strings_section(void);
SN_SECTION_LOCAL const SNStaticSection *
scalanative_static_classinfo_section(void);
SN_SECTION_LOCAL const SNStaticSection *
scalanative_static_globals_section(void);
SN_SECTION_LOCAL const SNStaticSection *
scalanative_static_classlist_section(void);

/* Registers the image containing the runtime. Images register themselves from
 * a constructor, but nothing else refers to this object: a static library
 * would leave it out without this reference from the runtime.
 */
SN_SECTION_LOCAL void scalanative_register_current_image(void);

/* The registry of loaded images, kept by the runtime, see staticRoots.c */
void scalanative_register_image(const SNImageSections *image);
size_t scalanative_images_count(void);
const SNImageSections *scalanative_image_at(size_t index);

#ifdef __cplusplus
}
#endif

#endif
