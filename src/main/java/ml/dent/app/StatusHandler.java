package ml.dent.app;

import javafx.beans.InvalidationListener;
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
import javafx.stage.Window;
import ml.dent.util.DaemonThreadFactory;
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

    private Node loadingGraphic;

    private ScheduledExecutorService updateExecutor;
    private Runnable                 updateStatus;

    private Window window;

    private static StatusHandler instance;

    StatusHandler(Label leftStatus, Label rightStatus, Window window) {
        if (instance != null) {
            throw new IllegalStateException("StatusHandler already instantiated, use getInstance() to get an instance");
        }
        instance = this;

        this.window = window;

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
                    setRightGraphic(loadingGraphic);
                }
            } else {
                processes.stream().findFirst().ifPresent(n -> setRightText(n.getName()));
                setRightGraphic(loadingGraphic);
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
        updateExecutor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory());
        updateExecutor.scheduleAtFixedRate(updateStatus, 0L, 60L, TimeUnit.SECONDS);
    }

    public static StatusHandler getInstance() {
        return instance;
    }

    void setLoadingGraphic(Node graphic) {
        loadingGraphic = graphic;
    }

    void setupEventLog(Pane container, TextArea eventLog, Button closeEventLog) {
        this.eventLog = eventLog;
        container.managedProperty().bind(container.visibleProperty());
        container.setVisible(false);
        leftStatus.setOnMouseClicked(event -> container.setVisible(!container.isVisible()));
        closeEventLog.setOnAction(event -> container.setVisible(false));
    }

    public void offerOperation(String text, String completionText, ReadOnlyBooleanProperty isDone) {
        offerOperation(text, new SimpleStringProperty(completionText), isDone);
    }

    /**
     * Will display the value of completion text at the moment isDone is invalidated.
     * If the initial value of completion text is not changed, the initial value will be displayed.
     */
    public void offerOperation(String text, StringProperty completionText, ReadOnlyBooleanProperty isDone) {
        offerOperation(text, completionText, isDone, new SimpleBooleanProperty());
    }

    /**
     * Will display additional alert if onError is set to true the moment isDone is invalidated.
     */
    public void offerOperation(String text, StringProperty completionText, ReadOnlyBooleanProperty isDone, ReadOnlyBooleanProperty onError) {
        processes.add(new StatusJob(text, completionText, System.currentTimeMillis(), isDone, onError));
    }

    public void offerStatus(String text) {
        offerStatus(text, System.currentTimeMillis());
    }

    private void offerStatus(String text, long time) {
        offerStatusJob(new StatusJob(text, time));
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

    private void setRightGraphic(Node graphic) {
        UIUtil.runOnJFXThread(() -> rightGraphic.set(graphic));
    }

    private class StatusJob {
        private String                  name;
        private long                    timeStarted;
        private ReadOnlyBooleanProperty isDone;
        private ReadOnlyBooleanProperty onError;
        private ReadOnlyStringProperty  completionText;

        private InvalidationListener completionListener;

        public StatusJob(String name, StringProperty completionText, long timeStarted, ReadOnlyBooleanProperty isDone, ReadOnlyBooleanProperty onError) {
            this.name = name;
            this.timeStarted = timeStarted;
            this.isDone = isDone;
            this.completionText = completionText;
            this.onError = onError;

            completionListener = listener -> operationComplete();
            isDone.addListener(completionListener);
        }

        public StatusJob(String name, long timeStarted) {
            this.name = name;
            this.timeStarted = timeStarted;
        }

        private void operationComplete() {
            offerStatus(name, timeStarted);
            offerStatus(completionText.get());
            processes.remove(this);
            if (onError.get()) {
                UIUtil.showError("Error", completionText.get(), "Error during operation", window);
            }
            isDone.removeListener(completionListener);
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
