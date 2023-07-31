package javaprogramming.commonmistakes.concurrenttool.concurrenthashmapperformance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequestMapping("concurrenthashmapperformance")
@Slf4j
public class ConcurrentHashMapPerformanceController {

    private static int LOOP_COUNT = 10000000;
    private static int THREAD_COUNT = 10;
    private static int ITEM_COUNT = 10;

    @GetMapping("good")
    public String good() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("normaluse");
        Map<String, Long> normaluse = normaluse();
        stopWatch.stop();
        Assert.isTrue(normaluse.size() == ITEM_COUNT, "normaluse size error");
        Assert.isTrue(normaluse.entrySet().stream().mapToLong(item -> item.getValue()).reduce(0, Long::sum) == LOOP_COUNT, "normaluse count error");
        stopWatch.start("gooduse");
        Map<String, Long> gooduse = gooduse();
        stopWatch.stop();
        Assert.isTrue(gooduse.size() == ITEM_COUNT, "gooduse size error");
        Assert.isTrue(gooduse.entrySet().stream()
                        .mapToLong(item -> item.getValue())
                        .reduce(0, Long::sum) == LOOP_COUNT
                , "gooduse count error");
        log.info(stopWatch.prettyPrint());
        return "OK";
    }

    private Map<String, Long> normaluse() throws InterruptedException {
        ConcurrentHashMap<String, Long> freqs = new ConcurrentHashMap<>(ITEM_COUNT);
        //创建10个线程，每个线程都去for循环10000000次，他们的key都是item+当前第几个线程，这个线程
        //号是随机的，就是说你现在第一个线程，但是你put的时候是put的第二个线程的标志
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, LOOP_COUNT).parallel().forEach(i -> {
                    String key = "item" + ThreadLocalRandom.current().nextInt(ITEM_COUNT);
                    synchronized (freqs) {
                        if (freqs.containsKey(key)) {
                            freqs.put(key, freqs.get(key) + 1);
                        } else {
                            freqs.put(key, 1L);
                        }
                    }
                }
        ));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        return freqs;
    }

    private Map<String, Long> gooduse() throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> freqs = new ConcurrentHashMap<>(ITEM_COUNT);
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, LOOP_COUNT).parallel().forEach(i -> {
                    String key = "item" + ThreadLocalRandom.current().nextInt(ITEM_COUNT);
           /**
            *
            * computeIfAbsent(key, k -> new LongAdder()):
            * 这是ConcurrentHashMap的一个方法。它的作用是，
            * 当key在ConcurrentHashMap中不存在时，使用提供的lambda表达式来生成一个默认的
            * LongAdder对象并将其作为key的值放入ConcurrentHashMap中；
            * 如果key已经存在，则直接返回对应的LongAdder对象。这样，
            * 每个key都对应一个独立的LongAdder对象，不会出现线程竞争问题。
            * */
                    freqs.computeIfAbsent(key, k -> new LongAdder()).increment();
                }
        ));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        return freqs.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue().longValue())
                );
    }
}
