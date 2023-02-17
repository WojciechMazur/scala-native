package scala.scalanative.posix

import scala.scalanative.unsafe._

@extern object ucontext {
  type ucontext_t = CStruct0 // opaque

  @name("scalanative_sizeof_ucontext_t")
  def sizeof_ucontext_t: CSize = extern

  /* Get user context and store it in variable pointed to by UCP.  */
  def getcontext(ucp: Ptr[ucontext_t]): Int = extern

  /* Set user context from information of variable pointed to by UCP.  */
  def setcontext(ucp: Ptr[ucontext_t]): Int = extern

  /* Save current context in context variable pointed to by OUCP and set
     context from variable pointed to by UCP.  */
  def swapcontext(oucp: Ptr[ucontext_t], ucp: Ptr[ucontext_t]): Int = extern

  /* Manipulate user context UCP to continue with calling functions FUNC
     and the ARGC-1 parameters following ARGC when the context is used
     the next time in `setcontext' or `swapcontext'.

     We cannot say anything about the parameters FUNC takes; `void'
     is as good as any other choice.  */
  def makecontext(
      ucp: Ptr[ucontext_t],
      func: CFuncPtr,
      argc: Int,
      args: CVarArgList
  ): Unit = extern

}
