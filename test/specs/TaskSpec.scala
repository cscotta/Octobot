import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers

import org.apache.log4j.Logger
import org.apache.log4j.BasicConfigurator

import com.urbanairship.octobot.QueueConsumer

class TaskSpec extends Spec with MustMatchers {

  // Our sample tasks to be executed below.
  val shouldSucceed = "{\"task\":\"com.urbanairship.octobot.tasks.SampleTask\"}"
  val noRunMethod = "{\"task\":\"com.urbanairship.octobot.tasks.SampleNonRunnableTask\"}"
  val nonExistent = "{\"task\":\"this.does.not.Exist\"}"
  val retry3x = "{\"task\":\"this.does.not.Exist\", \"retries\":3}"

  describe("Tasks") {

    it ("should execute a task successfully") {
      val queueConsumer = new QueueConsumer(null)
      queueConsumer.invokeTask(shouldSucceed) must be === true
    }


    it ("should fail to run a task with a non-existent run method gracefully.") {
      val queueConsumer = new QueueConsumer(null)
      println("Following expected to fail due to lack of a static run method.")
      queueConsumer.invokeTask(noRunMethod) must be === false
    }


    it ("should fail to run a non-existent task gracefully.") {
      val queueConsumer = new QueueConsumer(null)
      println("Following task is expected to fail because it does not exist.")
      queueConsumer.invokeTask(nonExistent) must be === false
    }

    it ("should fail a task, then retry it 3 times when instructed by JSON.") {
      val queueConsumer = new QueueConsumer(null)
      println("Following task is expected to run and fail three times.")
      queueConsumer.invokeTask(retry3x) must be === false
    }

  }
}
