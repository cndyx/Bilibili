package main.java.org.pqh.task;

import main.java.org.pqh.util.TestSlf4j;
import org.apache.log4j.Logger;

import java.util.Observable;
import java.util.Observer;

/**
 * Created by reborn on 2016/11/19.
 */

public class Listener implements Observer {

    private static Logger log= TestSlf4j.getLogger(Listener.class);

    public void update(Observable o, Object arg) {
        log.error("TaskBili线程出现异常");
        TaskBili taskBili= (TaskBili) arg;
        taskBili.addObserver(this);
        new Thread(taskBili,"aid数据采集").start();
        log.error("TaskBili线程重启");
    }
}