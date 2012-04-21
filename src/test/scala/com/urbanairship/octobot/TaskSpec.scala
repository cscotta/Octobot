import com.codahale.simplespec.Spec

import com.urbanairship.octobot.QueueConsumer

import org.apache.log4j.Logger
import org.apache.log4j.BasicConfigurator

import org.junit.Test

class TaskSpec extends Spec {

  class `Tasks` {

    // Our sample tasks to be executed below.
    val shouldSucceed = "{\"task\":\"com.urbanairship.octobot.tasks.SampleTask\"}"
    val noRunMethod = "{\"task\":\"com.urbanairship.octobot.tasks.SampleNonRunnableTask\"}"
    val nonExistent = "{\"task\":\"this.does.not.Exist\"}"
    val retry3x = "{\"task\":\"this.does.not.Exist\", \"retries\":3}"
    
    @Test def `should execute a task successfully` {
      val queueConsumer = new QueueConsumer(null)
      queueConsumer.invokeTask(shouldSucceed) must be(true)
    }

    @Test def `should fail to run a task with a non-existent run method gracefully` {
      val queueConsumer = new QueueConsumer(null)
      println("Following expected to fail due to lack of a static run method.")
      queueConsumer.invokeTask(noRunMethod) must be(false)
    }

    @Test def `should fail to run a non-existent task gracefully` {
      val queueConsumer = new QueueConsumer(null)
      println("Following task is expected to fail because it does not exist.")
      queueConsumer.invokeTask(nonExistent) must be(false)
    }

    @Test def `should fail a task, then retry it 3 times when instructed by JSON` {
      val queueConsumer = new QueueConsumer(null)
      println("Following task is expected to run and fail three times.")
      queueConsumer.invokeTask(retry3x) must be(false)
    }
  }

}
