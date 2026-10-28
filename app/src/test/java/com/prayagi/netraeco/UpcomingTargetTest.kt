package com.prayagi.netraeco
import org.junit.Assert.*
import org.junit.Test
class UpcomingTargetTest {
 private fun app(id:String, eta:Long?)=UpcomingApp(id,id,"","",eta)
 @Test fun nearestPublishedTargetFirstUndatedLastStableTie(){
  assertEquals(listOf("past","near","tie","far","undated"),Upcoming.sorted(listOf(app("undated",null),app("far",200),app("past",1),app("near",100),app("tie",100)),emptyList()).map{it.id})
 }
 @Test fun targetTextCarriesExactIstDateAndTime(){
  assertEquals("Release target: 1 Jan 1970, 5:30 AM IST",Upcoming.targetLabel(0))
 }
 @Test fun sortingDoesNotChangePublishedTarget(){
  val a=app("a",100);assertEquals(a,Upcoming.sorted(listOf(a),null).single())
 }
}
