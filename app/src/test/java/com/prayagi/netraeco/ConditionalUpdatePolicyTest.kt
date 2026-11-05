package com.prayagi.netraeco
import org.junit.Assert.*
import org.junit.Test
class ConditionalUpdatePolicyTest {
 @Test fun platformThresholdsAreExactAndFutureUnknown() {
  assertNull(ConditionalUpdatePolicy.minimumTarget(30));assertEquals(29,ConditionalUpdatePolicy.minimumTarget(31));assertEquals(29,ConditionalUpdatePolicy.minimumTarget(32));assertEquals(30,ConditionalUpdatePolicy.minimumTarget(33));assertEquals(31,ConditionalUpdatePolicy.minimumTarget(34));assertEquals(33,ConditionalUpdatePolicy.minimumTarget(35));assertEquals(34,ConditionalUpdatePolicy.minimumTarget(36));assertNull(ConditionalUpdatePolicy.minimumTarget(37))
 }
 @Test fun everyGateMustPass() {
  assertTrue(ConditionalUpdatePolicy.eligible(35,36,true,true,true,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(30,36,true,true,true,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,32,true,true,true,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,36,false,true,true,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,36,true,false,true,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,36,true,true,false,true,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,36,true,true,true,false,true))
  assertFalse(ConditionalUpdatePolicy.eligible(35,36,true,true,true,true,false))
 }
}
