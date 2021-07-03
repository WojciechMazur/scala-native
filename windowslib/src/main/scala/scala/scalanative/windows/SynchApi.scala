package scala.scalanative.windows

import scala.scalanative.unsafe._

@extern
object SynchApi {
  type CriticalSection   = Ptr[Byte]
  type ConditionVariable = Ptr[Byte]

  @name("scalanative_sizeof_CriticalSection")
  def SizeOfCriticalSection: CSize = extern

  @name("scalanative_sizeof_ConditionVariable")
  def SizeOfConditionVariable: CSize = extern

  def InitializeConditionVariable(conditionVariable: ConditionVariable): Unit =
    extern
  def InitializeCriticalSection(criticalSection: CriticalSection): Unit =
    extern
  def InitializeCriticalSectionAndSpinCount(criticalSection: CriticalSection,
                                            spinCount: DWord): Boolean = extern
  def InitializeCriticalEx(criticalSection: CriticalSection,
                           spinCount: DWord,
                           flags: DWord): Boolean                   = extern
  def DeleteCriticalSection(criticalSection: CriticalSection): Unit = extern

  def SetCriticalSectionSpinCount(criticalSection: CriticalSection,
                                  spinCount: DWord): DWord = extern

  def TryEnterCriticalSection(criticalSection: CriticalSection): Boolean =
    extern
  def EnterCriticalSection(criticalSection: CriticalSection): Unit = extern
  def LeaveCriticalSection(criticalSection: CriticalSection): Unit = extern

  def Sleep(milliseconds: DWord): Unit = extern
  def SleepConditionVariableCS(conditionVariable: ConditionVariable,
                               criticalSection: CriticalSection,
                               milliseconds: DWord): Boolean = extern
  def WakeAllConditionVariable(conditionVariable: ConditionVariable): Unit =
    extern
  def WakeConditionVariable(conditionVariable: ConditionVariable): Unit = extern

}
