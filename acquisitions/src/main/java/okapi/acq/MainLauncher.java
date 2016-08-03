package okapi.acq;

import io.vertx.core.Launcher;

public class MainLauncher extends Launcher {

  public static void main(String[] args) {
    MainLauncher mainLauncher = new MainLauncher();
    mainLauncher.dispatch(args);
  }

}