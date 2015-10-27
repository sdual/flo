package io.rouz.task;

import io.rouz.task.dsl.TaskBuilder;
import io.rouz.task.dsl.TaskBuilder.F0;
import io.rouz.task.dsl.TaskBuilder.F1;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class TaskTest {

  @Test
  public void shouldRunAsExpected() throws Exception {
    Task<EvenResult> wasEven = isEven(6);
    Task<EvenResult> madeEven = isEven(5);

    assertThat(wasEven.out(), instanceOf(WasEven.class));
    assertThat(wasEven.out().result(), is(6));
    assertThat(madeEven.out(), instanceOf(MadeEven.class));
    assertThat(madeEven.out().result(), is(10));
  }

  @Test
  public void shouldMemoizeTaskProcessing() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    Task<Integer> count = Task.named("Count")
        .process(counter::incrementAndGet);

    Task<Integer> sum = Task.named("Sum")
        .in(() -> count)
        .in(() -> count)
        .in(() -> count)
        .process((a, b, c) -> a + b + c);

    assertThat(sum.out(), is(3));
    assertThat(counter.get(), is(1)); // only called once

    // only memoized during each execution
    assertThat(count.out(), is(2));
    assertThat(count.out(), is(3));
    assertThat(counter.get(), is(3)); // called twice more
  }

  @Test
  public void shouldHanleStreamParameters() throws Exception {
    F0<Task<Integer>> countSupplier = countConstructor();

    // 1,2,3,4,5
    Stream<Task<Integer>> fiveInts = Stream
        .generate(countSupplier)
        .limit(5);

    Task<Integer> sum = Task.named("Sum")
        .ins(() -> fiveInts)
        .process(this::sumInts);

    // 1+2+3+4+5 = 15
    assertThat(sum.out(), is(15));
  }

  @Test
  public void shouldHanleMixedStreamAndPlainParameters() throws Exception {
    F0<Task<Integer>> countSupplier = countConstructor();

    // 1,2,3,4,5
    Stream<Task<Integer>> fiveInts = Stream
        .generate(countSupplier)
        .limit(5);

    Task<Integer> sum = Task.named("Sum")
        .in(() -> isEven(5))
        .ins(() -> fiveInts)
        .in(() -> isEven(2))
        .process((a, ints, b) -> a.result() + sumInts(ints) + b.result());

    // (5*2) + (1+2+3+4+5) + 2 = 27
    assertThat(sum.out(), is(27));
  }

  @Test
  public void shouldHanleMultipleStreamParameters() throws Exception {
    F0<Task<Integer>> countSupplier = countConstructor();

    F0<Stream<Task<Integer>>> fiveInts = () -> Stream
        .generate(countSupplier)
        .limit(5);

    Task<Integer> sum = Task.named("Sum")
        .ins(fiveInts)
        .ins(fiveInts)
        .process((first5, second5) -> sumInts(first5) + sumInts(second5));

    // (1+2+3+4+5) + (6+7+8+9+10) = 55
    assertThat(sum.out(), is(55));
  }

  @Test
  public void shoulAllowMultipleRunsWithStreamParameters() throws Exception {
    F0<Task<Integer>> countSupplier = countConstructor();

    F0<Stream<Task<Integer>>> fiveInts = () -> Stream
        .generate(countSupplier)
        .limit(5);

    Task<Integer> sum = Task.named("Sum")
        .ins(fiveInts)
        .process(this::sumInts);

    // discard first five
    sum.out();

    // 6+7+8+9+10 = 40
    assertThat(sum.out(), is(40));
  }

  @Test
  public void shouldMultipleRunsWithMultipleStreamParameters() throws Exception {
    F0<Task<Integer>> countSupplier = countConstructor();

    F0<Stream<Task<Integer>>> fiveInts = () -> Stream
        .generate(countSupplier)
        .limit(5);

    Task<Integer> sum = Task.named("Sum")
        .ins(fiveInts)
        .ins(fiveInts)
        .process((first5, second5) -> sumInts(first5) + sumInts(second5));

    // discard first 2 groups of five
    sum.out();

    // (11+12+13+14+15) + (16+17+18+19+20) = 155
    assertThat(sum.out(), is(155));
  }

  @Test
  public void shouldLinearizeTasks() throws Exception {
    Task<String> top = Task.named("Top")
        .in(() -> isEven(0))
        .in(() -> isEven(1))
        .process((a, b) -> "done");

    List<TaskId> taskIds = top.tasksInOrder()
        .collect(toList());

    TaskId isEven1Id = isEven(1).id();
    TaskId evenify1Id = evenify(1).id();

    assertThat(taskIds, containsInOrder(evenify1Id, isEven1Id));
  }

  @Test
  public void shouldFlattenStreamParameters() throws Exception {
    Task<String> top = Task.named("Top")
        .ins(() -> Stream.of(isEven(0), isEven(1)))
        .process(results -> "done " + results.size());

    List<TaskId> taskIds = top.tasksInOrder()
        .collect(toList());

    TaskId isEven1Id = isEven(1).id();
    TaskId evenify1Id = evenify(1).id();

    assertThat(taskIds.size(), is(3));
    assertThat(taskIds, containsInOrder(evenify1Id, isEven1Id));
  }

  @Test
  public void shouldLinearizeMixedStreamAndPlainParameters() throws Exception {
    F1<Integer, Task<Integer>> evenResult = n ->
        Task.named("EvenResult", n)
            .in(() -> isEven(n))
            .process(EvenResult::result);

    Task<Integer> sum = Task.named("Sum")
        .in(() -> isEven(5))
        .ins(() -> Stream.of(evenResult.apply(0), evenResult.apply(1)))
        .ins(() -> Stream.of(evenResult.apply(3)))
        .process((a, ints, b) -> a.result() + sumInts(ints) + sumInts(b));

    List<TaskId> taskIds = sum.tasksInOrder()
        .collect(toList());

    TaskId evenify5Id = evenify(5).id();
    TaskId evenify1Id = evenify(1).id();
    TaskId evenify3Id = evenify(3).id();

    assertThat(taskIds.size(), is(10));
    assertThat(taskIds, containsInOrder(evenify5Id, evenify1Id));
    assertThat(taskIds, containsInOrder(evenify5Id, evenify3Id));
    assertThat(taskIds, containsInOrder(evenify1Id, evenify3Id));
  }

  private F0<Task<Integer>> countConstructor() {
    AtomicInteger counter = new AtomicInteger(0);
    return () -> {
      int n = counter.incrementAndGet();
      return Task.named("Count", n)
          .process(() -> n);
    };
  }

  private int sumInts(List<Integer> intsList) {
    return intsList.stream().reduce(0, (a, b) -> a + b);
  }

  private Task<EvenResult> isEven(int n) {
    TaskBuilder isEven = Task.named("IsEven", n);

    if (n % 2 == 0) {
      return isEven.process(() -> new WasEven(n));
    }

    return isEven
        .in(() -> evenify(n))
        .process(MadeEven::new);
  }

  private Task<Integer> evenify(int n) {
    return Task.named("Evenify", n).process(() -> n * 2);
  }

  // Result ADT
  static abstract class EvenResult {

    private final int result;

    protected EvenResult(int result) {
      this.result = result;
    }

    int result() {
      return result;
    }
  }

  static class WasEven extends EvenResult {

    protected WasEven(int result) {
      super(result);
    }
  }

  static class MadeEven extends EvenResult {

    protected MadeEven(int result) {
      super(result);
    }
  }

  static <T> Matcher<Iterable<? extends T>> containsInOrder(T a, T b) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);
    return new TypeSafeMatcher<Iterable<? extends T>>() {

      @Override
      protected boolean matchesSafely(Iterable<? extends T> ts) {
        int ai = -1, bi = -1, i = 0;
        for (T t : ts) {
          if (a.equals(t)) {
            ai = i;
          }
          if (b.equals(t)) {
            bi = i;
          }
          i++;
        }

        return ai > -1 && bi > -1 && ai < bi;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Contains ");
        description.appendValue(a);
        description.appendText(" before ");
        description.appendValue(b);
      }
    };
  }
}
