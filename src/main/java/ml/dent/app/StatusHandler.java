package ml.dent.app;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import ml.dent.util.UIUtil;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StatusHandler {

    private StringProperty leftText  = new SimpleStringProperty("");
    private StringProperty rightText = new SimpleStringProperty("");

    private StatusJob currentStatus;

    private ObjectProperty<Node> rightGraphic = new SimpleObjectProperty<>();

    private Deque<StatusJob>         statusMessages = new LinkedList<>();
    private ObservableSet<StatusJob> processes      = FXCollections.observableSet(new HashSet<>());

    private Label    leftStatus;
    private TextArea eventLog;

    private ScheduledExecutorService updateExecutor;
    private Runnable                 updateStatus;

    public StatusHandler(Label leftStatus, Label rightStatus, Node loadingGraphic) {
        this.leftStatus = leftStatus;
        leftStatus.textProperty().bind(leftText);
        rightStatus.textProperty().bind(rightText);

        rightStatus.graphicProperty().bind(rightGraphic);
        rightStatus.setContentDisplay(ContentDisplay.LEFT);

        processes.addListener((SetChangeListener<StatusJob>) listener -> {
            if (processes.isEmpty()) {
                setRightText(null);
            } else if (listener.wasRemoved()) {
                if (listener.getElementRemoved().getName().equals(rightText.get())) {
                    processes.stream().findFirst().ifPresent(n -> setRightText(n.getName()));
                    rightGraphic.set(loadingGraphic);
                }
            } else {
                processes.stream().findFirst().ifPresent(n -> setRightText(n.getName()));
                rightGraphic.set(loadingGraphic);
            }
        });

        rightStatus.setOnMouseClicked(event -> {
            //Stage box = new Stage(StageStyle.UNDECORATED);
            // TODO open small window showing all running processes
        });
        updateStatus = () -> {
            if (currentStatus != null) {
                setLeftText(currentStatus.toString());
            }
        };
        updateExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread res = new Thread(runnable);
            res.setDaemon(true);
            return res;
        });
        updateExecutor.scheduleAtFixedRate(updateStatus, 0L, 60L, TimeUnit.SECONDS);
    }

    public void setupEventLog(Pane container, TextArea eventLog, Button closeEventLog) {
        this.eventLog = eventLog;
        container.managedProperty().bind(container.visibleProperty());
        container.setVisible(false);
        leftStatus.setOnMouseClicked(event -> container.setVisible(!container.isVisible()));
        closeEventLog.setOnAction(event -> container.setVisible(false));
    }

    /**
     * Will display the value of completion text at the moment isDone is invalidated.
     * If the initial value of completion text is not changed, the initial value will be displayed.
     */
    public void offerOperation(String text, StringProperty completionText, BooleanProperty isDone) {
        offerOperation(text, completionText, isDone, new SimpleBooleanProperty());
    }

    /**
     * Will display additional alert if onError is set to true the moment isDone is invalidated.
     */
    public void offerOperation(String text, StringProperty completionText, BooleanProperty isDone, BooleanProperty onError) {
        processes.add(new StatusJob(text, completionText, System.currentTimeMillis(), isDone, onError));
    }

    public void offerStatus(String text) {
        offerStatusJob(new StatusJob(text, System.currentTimeMillis()));
        StatusJob latest = statusMessages.peekLast();
        if (latest != null) {
            currentStatus = latest;
            updateExecutor.execute(updateStatus);
        }
    }

    private void offerStatusJob(StatusJob status) {
        statusMessages.offerLast(status);
        eventLog.appendText(status.dateFormat());
        eventLog.appendText("\n\n");
    }

    private void setLeftText(String val) {
        UIUtil.runOnJFXThread(() -> leftText.set(val));
    }

    private void setRightText(String val) {
        UIUtil.runOnJFXThread(() -> {
            if (val == null) {
                rightText.set("");
                rightGraphic.set(null);
            } else {
                rightText.set(val);
            }
        });
    }

    private class StatusJob {
        private String name;
        private long   timeStarted;

        public StatusJob(String name, StringProperty completionText, long timeStarted, BooleanProperty isDone, BooleanProperty onError) {
            this.name = name;
            this.timeStarted = timeStarted;

            isDone.addListener(listener -> {
                offerStatus(completionText.get());
                processes.remove(this);
                if (onError.get()) {
                    // TODO display alert to gui
                }
            });
        }

        public StatusJob(String name, long timeStarted) {
            this.name = name;
            this.timeStarted = timeStarted;
        }

        public String getName() {
            return name;
        }

        public String dateFormat() {
            Date cur = new Date(timeStarted);
            String formattedDate = new SimpleDateFormat("MM/dd/yyyy hh:mm a").format(cur);
            return formattedDate + " " + name;
        }

        @Override
        public String toString() {
            long difference = System.currentTimeMillis() - timeStarted;
            long days = TimeUnit.MILLISECONDS.toDays(difference);
            difference -= TimeUnit.DAYS.toMillis(days);
            long hours = TimeUnit.MILLISECONDS.toHours(difference);
            difference -= TimeUnit.HOURS.toMillis(hours);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(difference);

            StringBuilder res = new StringBuilder();
            res.append(name).append(" (");

            if (days != 0) {
                res.append(days).append(days == 1? " day" : " days").append(" ");
            }
            if (hours != 0) {
                res.append(hours).append(days == 1? " hour" : " hours").append(" ");
            }
            if (minutes != 0) {
                res.append(minutes).append(minutes == 1? " minute" : " minutes").append(" ");
            }

            if (days == 0 && hours == 0 && minutes == 0) {
                res.append("moments ");
            }

            res.append("ago)");
            return res.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            StatusJob statusJob = (StatusJob) o;
            return Objects.equals(name, statusJob.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
