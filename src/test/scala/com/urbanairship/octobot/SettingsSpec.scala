import com.codahale.simplespec._

import com.urbanairship.octobot.Settings

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import org.junit._

class SettingsSpec extends Spec {
  // Initialize Log4J
  BasicConfigurator.configure();
  
  class `Settings` {
    @Test def `should return a value for a setting in /usr/local/octobot/octobot.yml` {
      val startupHook = Settings.get("Octobot", "startup_hook")
      startupHook must be("com.urbanairship.tasks.StartupHook")
    }

    @Test def `should return a default value for a setting` {
      val nonexistentSetting = Settings.getAsInt("Octobot", "legs", 3)
      nonexistentSetting must be(3)
      val anotherBadSetting = Settings.get("Octobot", "name", "Charles")
      anotherBadSetting must be("Charles")
    }
  }
}
