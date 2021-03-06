package ml.dent.app;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.Cursor;
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
import java.util.concurrent.atomic.AtomicInteger;

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

    private static int verbosity;

    public static final int NONE    = -1;
    public static final int FATAL   = 1;
    public static final int ERROR   = 2;
    public static final int WARNING = 3;
    public static final int INFO    = 4;
    public static final int DEBUG   = 5;
    public static final int MORE    = 6;
    public static final int ALL     = Integer.MAX_VALUE;

    HashMap<String, Integer> levelMap = new LinkedHashMap<String, Integer>() {
        {
            put("NONE", NONE);
            put("FATAL", FATAL);
            put("ERROR", ERROR);
            put("WARNING", WARNING);
            put("INFO", INFO);
            put("DEBUG", DEBUG);
            put("MORE", MORE);
            put("ALL", ALL);
        }
    };

    StatusHandler(Label leftStatus, Label rightStatus, Window window) {
        if (instance != null) {
            throw new IllegalStateException("StatusHandler already instantiated, use getInstance() to get an instance");
        }
        instance = this;
        verbosity = INFO;
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
                    processes.stream()
                            .filter(n -> n.logLevel <= verbosity) // only get elements with printable log levels
                            .findFirst() // get the "first" one
                            .ifPresent(n -> {
                                setRightText(n.getName());
                                setRightGraphic(loadingGraphic);
                            });
                }
            } else {
                processes.stream()
                        .filter(n -> n.logLevel <= verbosity) // only get elements with printable log levels
                        .findFirst() // get the "first" one
                        .ifPresent(n -> {
                            setRightText(n.getName());
                            setRightGraphic(loadingGraphic);

                        });
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

    void setVerbosity(int level) {
        verbosity = level;
    }

    int getVerbosity() {
        return verbosity;
    }

    int getVerbosityFromString(String str) {
        return levelMap.get(str);
    }

    String getVerbosityAsString(int verbosity) {
        Set<Map.Entry<String, Integer>> entries = levelMap.entrySet();
        for (Map.Entry<String, Integer> entry : entries) {
            if (entry.getValue() == verbosity) {
                return entry.getKey();
            }
        }
        return null;
    }

    void setupEventLog(Pane container, TextArea eventLog, Button closeEventLog) {
        this.eventLog = eventLog;
        container.managedProperty().bind(container.visibleProperty());
        container.setVisible(false);
        leftStatus.setOnMouseClicked(event -> container.setVisible(!container.isVisible()));
        closeEventLog.setOnAction(event -> container.setVisible(false));
    }

    public void offerOperation(String text, String completionText, ReadOnlyBooleanProperty isDone) {
        offerOperation(text, new SimpleStringProperty(completionText), isDone, INFO);
    }

    public void offerOperation(String text, String completionText, ReadOnlyBooleanProperty isDone, int level) {
        offerOperation(text, new SimpleStringProperty(completionText), isDone, level);
    }

    /**
     * Will display the value of completion text at the moment isDone is invalidated.
     * If the initial value of completion text is not changed, the initial value will be displayed.
     */
    public void offerOperation(String text, StringProperty completionText, ReadOnlyBooleanProperty isDone, int level) {
        offerOperation(text, completionText, isDone, new SimpleBooleanProperty(), level);
    }

    /**
     * Will display additional alert if onError is set to true the moment isDone is invalidated.
     */
    public void offerOperation(String text, StringProperty completionText, ReadOnlyBooleanProperty isDone, ReadOnlyBooleanProperty onError, int level) {
        processes.add(new StatusJob(text, completionText, System.currentTimeMillis(), isDone, onError, level));
    }

    /**
     * @param error      The error to be logged
     * @param headerText The header text to be displayed to the user
     */
    public void offerError(String error, String headerText) {
        offerStatus(error, ERROR);
        UIUtil.showError("Error", error, headerText, window);
    }

    public void offerStatus(String text, int level) {
        offerStatus(text, System.currentTimeMillis(), level);
    }

    private void offerStatus(String text, long time, int level) {
        offerStatusJob(new StatusJob(text, time, level));
        StatusJob latest = statusMessages.peekLast();
        if (latest != null) {
            currentStatus = latest;
            updateExecutor.execute(updateStatus);
        }
    }

    private void offerStatusJob(StatusJob status) {
        if (status.logLevel <= verbosity) {
            statusMessages.offerLast(status);
            appendToLog(status.dateFormat() + "\n\n");
        }
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

    private void appendToLog(String str) {
        UIUtil.runOnJFXThread(() -> {
            double scrollTop = eventLog.getScrollTop(); // save scroll position
            int pos = eventLog.getCaretPosition();
            eventLog.appendText(str);
            eventLog.setScrollTop(scrollTop); // restore position to prevent auto scrolling to new text
            eventLog.positionCaret(pos);
        });
    }

    private static AtomicInteger operationInProgress = new AtomicInteger(0);

    private class StatusJob {
        private String                  name;
        private long                    timeStarted;
        private int                     logLevel;
        private ReadOnlyBooleanProperty isDone;
        private ReadOnlyBooleanProperty onError;
        private ReadOnlyStringProperty  completionText;

        private InvalidationListener completionListener;

        public StatusJob(String name, StringProperty completionText, long timeStarted, ReadOnlyBooleanProperty isDone, ReadOnlyBooleanProperty onError, int logLevel) {
            this.name = name;
            this.timeStarted = timeStarted;
            this.isDone = isDone;
            this.completionText = completionText;
            this.onError = onError;
            this.logLevel = logLevel;

            UIUtil.runOnJFXThread(() -> {
                operationInProgress.getAndIncrement();
                if (window.getScene() != null) {
                    window.getScene().setCursor(Cursor.WAIT);
                }
            });

            completionListener = listener -> operationComplete();
            isDone.addListener(completionListener);
        }

        public StatusJob(String name, long timeStarted, int logLevel) {
            this.name = name;
            this.timeStarted = timeStarted;
            this.logLevel = logLevel;
        }

        private void operationComplete() {
            offerStatus(name, timeStarted, logLevel);
            offerStatus(completionText.get(), logLevel);
            processes.remove(this);
            if (onError.get()) {
                offerError(completionText.get(), "Error during operation");
            }
            isDone.removeListener(completionListener);
            operationInProgress.getAndDecrement();
            if (operationInProgress.get() <= 0 && window.getScene() != null) {
                window.getScene().setCursor(Cursor.DEFAULT);
            }
        }

        public String getName() {
            return name;
        }

        public String dateFormat() {
            Date cur = new Date(timeStarted);
            String formattedDate = new SimpleDateFormat("MM/dd/yyyy hh:mm a").format(cur);
            return formattedDate + " " + getVerbosityAsString(logLevel) + ": " + name;
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
