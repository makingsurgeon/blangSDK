package blang

import blang.runtime.Runner
import org.junit.Assert
import org.junit.Test
import blang.validation.internals.fixtures.Doomsday
import blang.runtime.SampledModel

class TestEndToEnd {
  
  @Test
  def void doomsday() {
    
    SampledModel::check = true
    
    for (engine : #["SCM", "PT"]) {
      Assert.assertEquals(
        0, 
        Runner::start(
          "--model", Doomsday.canonicalName,
          "--model.rate", "1.0",
          "--model.z", "NA",
          "--model.y", "1.2",
          "--engine", engine
        )
      )
    }
  }
  
}