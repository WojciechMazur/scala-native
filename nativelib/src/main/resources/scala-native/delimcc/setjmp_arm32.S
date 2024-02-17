#if defined(__arm__) && (defined(__linux__) || defined(__APPLE__))

/*
WARNING: Converted from setjmp_arm64.S using LLM, not guaranteed to work correctly
jump_buf layout:
   0: lr   = r14
   4: fp   = r11
   8: r7
  12: r8
  16: r9
  20: r10
  24: r4
  28: r5
  32: r6
  36: r0
  40: r1
  44: r2
  48: r3
  52: r12
  56: sp   = r13
  60: sizeof jmp_buf
*/

.global _lh_setjmp
.global _lh_longjmp
.global _lh_boundary_entry
.global _lh_resume_entry
.global _lh_get_sp

.balign 4
/* called with r0: &jmp_buf */
_lh_setjmp:                 
    stmia r0!, {lr, fp, r7, r8, r9, r10, r11, r12}  /* Save registers lr, fp, r7-r12 to jmp_buf */
    mov   r1, sp                   /* sp to r1 */
    str   r1, [r0, #56]            /* Save sp to jmp_buf */
    mov   r1, #0                   /* always return zero */
    bx    lr                       /* jump to lr */

.balign 4
/* called with r0: &jmp_buf, r1: value to return */
_lh_longjmp:
    ldmia r0!, {lr, fp, r7, r8, r9, r10, r11, r12}   /* Load registers lr, fp, r7-r12 from jmp_buf */
    ldr   sp, [r0, #56]            /* Load sp from jmp_buf */
    mov   r2, #0                   /* Clear register r2 (not used in this implementation) */
    bx    lr                       /* jump to lr */

.balign 4
_lh_boundary_entry:
    mov   r2, r1
    mov   r1, r0
    mov   r0, sp
    sub   sp, sp, #12
    str   lr, [sp, #4]             /* 4-byte Folded Spill */
    bl    __continuation_boundary_impl
    ldr   lr, [sp, #4]             /* 4-byte Folded Spill */
    add   sp, sp, #12
    bx    lr

.balign 4
_lh_resume_entry: /* r0 = cont_size, r1 = cont, r2 = arg */
    sub   sp, sp, r0
    add   r0, sp, r0
    mov   r3, lr /* copy lr */
    bl    __continuation_resume_impl /* it will just return from here */

.balign 4
_lh_get_sp:
    mov   r0, sp
    bx    lr
#endif

#if defined(__linux__) && defined(__ELF__)
/* Reference:
 *   https://wiki.gentoo.org/wiki/Hardened/GNU_stack_quickstart
 */

.section .note.GNU-stack,"",%progbits

#endif
