package compiling;

import io.rouz.task.Task;

import java.util.Map;

import javax.annotation.Generated;

@Generated("io.rouz.task.processor.TaskBindingProcessor")
public final class FloRootTaskFactory {

  private FloRootTaskFactory() {
    // no instantiation
  }

  public static Task<String> simple(Map<String, String> $args) {
    return PlainTaskConstructor.simple();
  }
}
