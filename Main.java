import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * ICS26012 - CPU & Disk Scheduling
 * Implemented:
 *  - Shortest Job First (SJF)
 *  - Multilevel Queue (MLQ: Q1=RR, Q2=RR, Q3=FCFS)
 *  - Disk Scan
 *  - Disk Look
 *
 * For SJF and MLQ:
 *  - Single process table:
 *      PID, Arrival Time, Burst Time, Completion Time, Turnaround Time, Waiting Time
 *  - Arrival/Burst editable, others computed.
 *  - Gantt chart: bar graph (no colors, white bars, black border/labels).
 *
 * Statistics for CPU:
 *  - Only Avg Completion Time, Avg Turnaround Time, Avg Waiting Time are shown.
 *
 * On closing an algorithm window (X):
 *  - Prompt: Yes = repeat input (reset), No = terminate entire program.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

/* ===========================
   MAIN APPLICATION FRAME
   =========================== */

class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("ICS26012 - CPU & Disk Scheduling");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 450);
        setLocationRelativeTo(null);

        add(createMainMenuPanel());
    }

    private JPanel createMainMenuPanel() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel menuPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        menuPanel.setBorder(BorderFactory.createTitledBorder("Main Menu"));

        JButton btnAbout = new JButton("[0] About");
        JButton btnSJF  = new JButton("[1] Shortest Job First (SJF)");
        JButton btnMLQ  = new JButton("[2] Multilevel Queue (MLQ: RR, RR, FCFS)");
        JButton btnScan = new JButton("[3] Disk - Scan");
        JButton btnLook = new JButton("[4] Disk - Look");
        JButton btnExit = new JButton("[5] Exit");

        btnAbout.addActionListener(e -> showAboutDialog());
        btnSJF.addActionListener(e -> new SJFFrame().setVisible(true));
        btnMLQ.addActionListener(e -> new MLQFrame().setVisible(true));
        btnScan.addActionListener(e -> new DiskFrame(DiskAlgorithmType.SCAN).setVisible(true));
        btnLook.addActionListener(e -> new DiskFrame(DiskAlgorithmType.LOOK).setVisible(true));
        btnExit.addActionListener(e -> System.exit(0));

        menuPanel.add(btnAbout);
        menuPanel.add(btnSJF);
        menuPanel.add(btnMLQ);
        menuPanel.add(btnScan);
        menuPanel.add(btnLook);
        menuPanel.add(btnExit);

        root.add(menuPanel, BorderLayout.CENTER);
        return root;
    }

    private void showAboutDialog() {
        String msg =
                "ICS26012 Final Project: CPU & Disk Scheduling \n\n" +
                "Group: groupsteddy\n" +
                "Members:\n" +
                "  - BALLESTER, Gracie - Shortest Job First (SJF)\n" +
                "  - DUARTE, Temz - Multilevel Queue (MLQ)\n" +
                "  - MANIPOL, Reian - Look\n" +
                "  - PAWITRA, Angel - Scan";
        JOptionPane.showMessageDialog(this, msg, "About", JOptionPane.INFORMATION_MESSAGE);
    }
}

/* ===========================
   GANTT CHART PANEL (bar graph, no colors)
   =========================== */

class GanttChartPanel extends JPanel {
    private List<GanttEntryCPU> segments = new ArrayList<>();

    public void setSegments(List<GanttEntryCPU> segments) {
        this.segments = segments == null ? new ArrayList<>() : new ArrayList<>(segments);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (segments == null || segments.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Total time
        int minTime = segments.stream().mapToInt(s -> s.start).min().orElse(0);
        int maxTime = segments.stream().mapToInt(s -> s.end).max().orElse(0);
        if (maxTime <= minTime) return;

        int width = getWidth() - 60;   // make chart wide; some padding
        int baseX = 30;
        int baseY = getHeight() / 2;

        double scale = width * 1.0 / (maxTime - minTime);

        // Time axis
        g2.setColor(Color.BLACK);
        g2.drawLine(baseX, baseY, baseX + width, baseY);

        // Ticks
        int maxTicks = 20;
        int totalUnits = maxTime - minTime;
        int step = 1;
        if (totalUnits > maxTicks) {
            step = (int) Math.ceil(totalUnits / (double) maxTicks);
        }

        for (int t = minTime; t <= maxTime; t += step) {
            int x = baseX + (int) ((t - minTime) * scale);
            g2.drawLine(x, baseY - 3, x, baseY + 3);
            g2.drawString(String.valueOf(t), x - 5, baseY + 15);
        }

        // Bars
        int barHeight = 30;
        int yTop = baseY - barHeight - 10;

        for (GanttEntryCPU seg : segments) {
            if (seg.end <= seg.start) continue;

            String pid = seg.pid;
            int x1 = baseX + (int) ((seg.start - minTime) * scale);
            int x2 = baseX + (int) ((seg.end - minTime) * scale);
            int w = x2 - x1;
            if (w <= 0) w = 1;

            // White fill, black border
            g2.setColor(Color.WHITE);
            g2.fillRect(x1, yTop, w, barHeight);
            g2.setColor(Color.BLACK);
            g2.drawRect(x1, yTop, w, barHeight);

            // Label PID
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(pid);
            int textX = x1 + (w - textWidth) / 2;
            int textY = yTop + (barHeight + fm.getAscent()) / 2 - 2;
            g2.drawString(pid, textX, textY);
        }
    }
}

/* ===========================
   CPU SJF FRAME
   =========================== */

class SJFFrame extends JFrame {

    private JTextField txtNumProc;
    private JTable tblProcesses;
    private DefaultTableModel procModel;
    private JTextArea txtStats;
    private GanttChartPanel ganttPanel;

    public SJFFrame() {
        setTitle("CPU Scheduling - Shortest Job First (SJF)");
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new ConfirmCloseListener(this, this::resetAll));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("SJF (Non-preemptive)"));

        p.add(new JLabel("Number of Processes [2-10]:"));
        txtNumProc = new JTextField("4", 5);
        p.add(txtNumProc);

        JButton btnSet = new JButton("Set");
        JButton btnReset = new JButton("Reset/Clear");
        JButton btnRun = new JButton("Run SJF");

        btnSet.addActionListener(this::onSetProc);
        btnReset.addActionListener(e -> resetAll());
        btnRun.addActionListener(this::onRun);

        p.add(btnSet);
        p.add(btnReset);
        p.add(btnRun);

        return p;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        procModel = new DefaultTableModel(
                new Object[]{"PID", "Arrival Time", "Burst Time", "Completion Time", "Turnaround Time", "Waiting Time"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 || column == 2; // Arrival and Burst editable
            }
        };
        tblProcesses = new JTable(procModel);
        JScrollPane spProc = new JScrollPane(tblProcesses);
        spProc.setBorder(new TitledBorder("Processes"));
        panel.add(spProc, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel p = new JPanel(new BorderLayout());

        // Gantt bar (wider area)
        ganttPanel = new GanttChartPanel();
        ganttPanel.setPreferredSize(new Dimension(850, 200)); // wide
        ganttPanel.setBorder(new TitledBorder("Gantt Chart"));
        p.add(ganttPanel, BorderLayout.CENTER);

        // Statistics (narrower area at right)
        txtStats = new JTextArea(6, 25);
        txtStats.setEditable(false);
        JScrollPane spStats = new JScrollPane(txtStats);
        spStats.setBorder(new TitledBorder("Statistics"));
        p.add(spStats, BorderLayout.EAST);

        return p;
    }

    private void onSetProc(ActionEvent e) {
        int n;
        try {
            n = Integer.parseInt(txtNumProc.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Number of processes must be numeric.");
            return;
        }
        if (n < 2 || n > 10) {
            showError("Number of processes must be between 2 and 10.");
            return;
        }
        procModel.setRowCount(0);
        for (int i = 0; i < n; i++) {
            procModel.addRow(new Object[]{"P" + (i + 1), 0, 0, "", "", ""});
        }
        txtStats.setText("");
        ganttPanel.setSegments(Collections.emptyList());
    }

    private void resetAll() {
        txtNumProc.setText("4");
        procModel.setRowCount(0);
        txtStats.setText("");
        ganttPanel.setSegments(Collections.emptyList());
    }

    private void onRun(ActionEvent e) {
        List<CPUProcess> list;
        try {
            list = readProcessesFromTable();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        ScheduleResultCPU result = CPUScheduler.scheduleSJF(list);

        Map<String, CPUProcess> map = new HashMap<>();
        for (CPUProcess p : result.processes) map.put(p.pid, p);

        for (int i = 0; i < procModel.getRowCount(); i++) {
            String pid = String.valueOf(procModel.getValueAt(i, 0));
            CPUProcess p = map.get(pid);
            if (p != null) {
                procModel.setValueAt(p.completionTime, i, 3);
                procModel.setValueAt(p.turnaroundTime, i, 4);
                procModel.setValueAt(p.waitingTime, i, 5);
            }
        }

        ganttPanel.setSegments(result.gantt);

        // Only avg CT, avg TAT, avg WT
        txtStats.setText("");
        txtStats.append(String.format("Avg Completion Time: %.2f ms%n", result.avgCT));
        txtStats.append(String.format("Avg Turnaround Time: %.2f ms%n", result.avgTAT));
        txtStats.append(String.format("Avg Waiting Time:    %.2f ms%n", result.avgWT));
    }

    private List<CPUProcess> readProcessesFromTable() {
        int rows = procModel.getRowCount();
        if (rows < 2) throw new IllegalArgumentException("At least 2 processes are required.");

        List<CPUProcess> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String pid = String.valueOf(procModel.getValueAt(i, 0)).trim();
            String atStr = String.valueOf(procModel.getValueAt(i, 1)).trim();
            String btStr = String.valueOf(procModel.getValueAt(i, 2)).trim();

            if (pid.isEmpty()) {
                throw new IllegalArgumentException("PID at row " + (i + 1) + " is empty.");
            }
            int at, bt;
            try {
                at = Integer.parseInt(atStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Arrival time at row " + (i + 1) + " must be numeric.");
            }
            try {
                bt = Integer.parseInt(btStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Burst time at row " + (i + 1) + " must be numeric.");
            }
            if (at < 0) {
                throw new IllegalArgumentException("Arrival time at row " + (i + 1) + " must be >= 0.");
            }
            if (bt <= 0) {
                throw new IllegalArgumentException("Burst time at row " + (i + 1) + " must be > 0.");
            }
            CPUProcess p = new CPUProcess();
            p.pid = pid;
            p.arrivalTime = at;
            p.burstTime = bt;
            list.add(p);
        }
        list.sort(Comparator
                .comparingInt((CPUProcess p) -> p.arrivalTime)
                .thenComparingInt(p -> CPUScheduler.extractPidNumber(p.pid)));
        return list;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
    }
}

/* ===========================
   CPU MLQ FRAME
   =========================== */

class MLQFrame extends JFrame {
    private JTextField txtNumProc, txtQ1, txtQ2;
    private JTable tblProcesses;
    private DefaultTableModel procModel;
    private JTextArea txtStats;
    private GanttChartPanel ganttPanel;

    public MLQFrame() {
        setTitle("CPU Scheduling - Multilevel Queue (Q1=RR, Q2=RR, Q3=FCFS)");
        setSize(1300, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new ConfirmCloseListener(this, this::resetAll));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("MLQ: Q1 = RR, Q2 = RR, Q3 = FCFS"));

        p.add(new JLabel("Number of Processes [2-10]:"));
        txtNumProc = new JTextField("6", 5);
        p.add(txtNumProc);

        p.add(new JLabel("Time Quantum Q1:"));
        txtQ1 = new JTextField("2", 5);
        p.add(txtQ1);

        p.add(new JLabel("Time Quantum Q2:"));
        txtQ2 = new JTextField("4", 5);
        p.add(txtQ2);

        JButton btnSet = new JButton("Set");
        JButton btnReset = new JButton("Reset/Clear");
        JButton btnRun = new JButton("Run MLQ");

        btnSet.addActionListener(this::onSetProc);
        btnReset.addActionListener(e -> resetAll());
        btnRun.addActionListener(this::onRun);

        p.add(btnSet);
        p.add(btnReset);
        p.add(btnRun);

        return p;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        procModel = new DefaultTableModel(
                new Object[]{"PID", "Arrival Time", "Burst Time", "Completion Time", "Turnaround Time", "Waiting Time"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 || column == 2; // Arrival and Burst editable
            }
        };
        tblProcesses = new JTable(procModel);
        JScrollPane spProc = new JScrollPane(tblProcesses);
        spProc.setBorder(new TitledBorder("Processes"));
        panel.add(spProc, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel p = new JPanel(new BorderLayout());

        ganttPanel = new GanttChartPanel();
        ganttPanel.setPreferredSize(new Dimension(900, 200)); // wider than stats
        ganttPanel.setBorder(new TitledBorder("Gantt Chart"));
        p.add(ganttPanel, BorderLayout.CENTER);

        txtStats = new JTextArea(6, 25);
        txtStats.setEditable(false);
        JScrollPane spStats = new JScrollPane(txtStats);
        spStats.setBorder(new TitledBorder("Statistics"));
        p.add(spStats, BorderLayout.EAST);

        return p;
    }

    private void onSetProc(ActionEvent e) {
        int n;
        try {
            n = Integer.parseInt(txtNumProc.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Number of processes must be numeric.");
            return;
        }
        if (n < 2 || n > 10) {
            showError("Number of processes must be between 2 and 10.");
            return;
        }
        procModel.setRowCount(0);
        for (int i = 0; i < n; i++) {
            procModel.addRow(new Object[]{"P" + (i + 1), 0, 0, "", "", ""});
        }
        txtStats.setText("");
        ganttPanel.setSegments(Collections.emptyList());
    }

    private void resetAll() {
        txtNumProc.setText("6");
        txtQ1.setText("2");
        txtQ2.setText("4");
        procModel.setRowCount(0);
        txtStats.setText("");
        ganttPanel.setSegments(Collections.emptyList());
    }

    private void onRun(ActionEvent e) {
        List<CPUProcess> list;
        int q1, q2;
        try {
            list = readProcessesFromTable();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        try {
            q1 = Integer.parseInt(txtQ1.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Time Quantum Q1 must be numeric.");
            return;
        }
        try {
            q2 = Integer.parseInt(txtQ2.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Time Quantum Q2 must be numeric.");
            return;
        }
        if (q1 <= 0 || q2 <= 0) {
            showError("Time quanta must be > 0.");
            return;
        }

        ScheduleResultCPU result = CPUScheduler.scheduleMLQ_RR_RR_FCFS(list, q1, q2);

        Map<String, CPUProcess> map = new HashMap<>();
        for (CPUProcess p : result.processes) map.put(p.pid, p);

        for (int i = 0; i < procModel.getRowCount(); i++) {
            String pid = String.valueOf(procModel.getValueAt(i, 0));
            CPUProcess p = map.get(pid);
            if (p != null) {
                procModel.setValueAt(p.completionTime, i, 3);
                procModel.setValueAt(p.turnaroundTime, i, 4);
                procModel.setValueAt(p.waitingTime, i, 5);
            }
        }

        ganttPanel.setSegments(result.gantt);

        // Only avg CT, TAT, WT
        txtStats.setText("");
        txtStats.append(String.format("Avg Completion Time: %.2f ms%n", result.avgCT));
        txtStats.append(String.format("Avg Turnaround Time: %.2f ms%n", result.avgTAT));
        txtStats.append(String.format("Avg Waiting Time:    %.2f ms%n", result.avgWT));
    }

    private List<CPUProcess> readProcessesFromTable() {
        int rows = procModel.getRowCount();
        if (rows < 2) throw new IllegalArgumentException("At least 2 processes are required.");

        List<CPUProcess> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String pid = String.valueOf(procModel.getValueAt(i, 0)).trim();
            String atStr = String.valueOf(procModel.getValueAt(i, 1)).trim();
            String btStr = String.valueOf(procModel.getValueAt(i, 2)).trim();

            if (pid.isEmpty()) {
                throw new IllegalArgumentException("PID at row " + (i + 1) + " is empty.");
            }
            int at, bt;
            try {
                at = Integer.parseInt(atStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Arrival time at row " + (i + 1) + " must be numeric.");
            }
            try {
                bt = Integer.parseInt(btStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Burst time at row " + (i + 1) + " must be numeric.");
            }
            if (at < 0) {
                throw new IllegalArgumentException("Arrival time at row " + (i + 1) + " must be >= 0.");
            }
            if (bt <= 0) {
                throw new IllegalArgumentException("Burst time at row " + (i + 1) + " must be > 0.");
            }

            CPUProcess p = new CPUProcess();
            p.pid = pid;
            p.arrivalTime = at;
            p.burstTime = bt;
            list.add(p);
        }

        list.sort(Comparator
                .comparingInt((CPUProcess p) -> p.arrivalTime)
                .thenComparingInt(p -> CPUScheduler.extractPidNumber(p.pid)));
        return list;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
    }
}

/* ===========================
   WINDOW CLOSE CONFIRMATION
   =========================== */

class ConfirmCloseListener extends WindowAdapter {
    private final JFrame frame;
    private final Runnable resetAction;

    public ConfirmCloseListener(JFrame frame, Runnable resetAction) {
        this.frame = frame;
        this.resetAction = resetAction;
    }

    @Override
    public void windowClosing(WindowEvent e) {
        int option = JOptionPane.showConfirmDialog(
                frame,
                "Do you want to enter inputs again?\nYes = repeat input, No = terminate program.",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (option == JOptionPane.YES_OPTION) {
            if (resetAction != null) resetAction.run();
        } else {
            System.exit(0);
        }
    }
}

/* ===========================
   CPU SCHEDULER CORE
   =========================== */

class CPUProcess {
    String pid;
    int arrivalTime;
    int burstTime;

    int startTime = -1;
    int completionTime;
    int turnaroundTime;
    int waitingTime;
    int responseTime;

    int remainingTime;
}

class GanttEntryCPU {
    int start;
    int end;
    String pid;
}

class ScheduleResultCPU {
    List<CPUProcess> processes;
    List<GanttEntryCPU> gantt;
    double avgTAT, avgWT, avgCT, avgRT, utilization, throughput;
}

class CPUScheduler {

    public static ScheduleResultCPU scheduleSJF(List<CPUProcess> input) {
        List<CPUProcess> procs = deepCopy(input);
        List<GanttEntryCPU> gantt = new ArrayList<>();

        int time = 0;
        int busy = 0;
        List<CPUProcess> remaining = new ArrayList<>(procs);

        while (!remaining.isEmpty()) {
            CPUProcess next = selectNextSJF(remaining, time);
            if (next == null) {
                int minArrival = remaining.stream().mapToInt(p -> p.arrivalTime).min().orElse(time);
                if (minArrival > time) {
                    gantt.add(makeGantt(time, minArrival, "IDLE"));
                    time = minArrival;
                }
                next = selectNextSJF(remaining, time);
                if (next == null) break;
            }

            if (next.startTime == -1) next.startTime = time;
            gantt.add(makeGantt(time, time + next.burstTime, next.pid));
            busy += next.burstTime;
            time += next.burstTime;

            next.completionTime = time;
            next.turnaroundTime = next.completionTime - next.arrivalTime;
            next.waitingTime = next.turnaroundTime - next.burstTime;
            next.responseTime = next.startTime - next.arrivalTime;

            remaining.remove(next);
        }

        return buildResult(procs, gantt, busy, time);
    }

    public static ScheduleResultCPU scheduleMLQ_RR_RR_FCFS(List<CPUProcess> input, int q1, int q2) {
        List<CPUProcess> procs = deepCopy(input);
        for (CPUProcess p : procs) {
            p.remainingTime = p.burstTime;
        }

        int n = procs.size();
        int sizeQ = Math.max(1, n / 3);
        List<CPUProcess> q1List = new ArrayList<>();
        List<CPUProcess> q2List = new ArrayList<>();
        List<CPUProcess> q3List = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            CPUProcess p = procs.get(i);
            if (i < sizeQ) {
                q1List.add(p);
            } else if (i < 2 * sizeQ) {
                q2List.add(p);
            } else {
                q3List.add(p);
            }
        }

        List<GanttEntryCPU> gantt = new ArrayList<>();
        int time = 0;
        int busy = 0;

        while (hasRemaining(q1List) || hasRemaining(q2List) || hasRemaining(q3List)) {
            boolean didWork = false;

            int[] tRef = {time};
            int[] bRef = {busy};
            didWork |= runRRQueue(q1List, q1, gantt, tRef, bRef);
            time = tRef[0];
            busy = bRef[0];

            tRef[0] = time;
            bRef[0] = busy;
            didWork |= runRRQueue(q2List, q2, gantt, tRef, bRef);
            time = tRef[0];
            busy = bRef[0];

            tRef[0] = time;
            bRef[0] = busy;
            didWork |= runFCFSQueue(q3List, gantt, tRef, bRef);
            time = tRef[0];
            busy = bRef[0];

            if (!didWork) {
                int nextArrival = Integer.MAX_VALUE;
                for (CPUProcess p : procs) {
                    if (p.remainingTime > 0 && p.arrivalTime > time && p.arrivalTime < nextArrival) {
                        nextArrival = p.arrivalTime;
                    }
                }
                if (nextArrival == Integer.MAX_VALUE) break;
                gantt.add(makeGantt(time, nextArrival, "IDLE"));
                time = nextArrival;
            }
        }

        return buildResult(procs, gantt, busy, time);
    }

    private static boolean hasRemaining(List<CPUProcess> list) {
        for (CPUProcess p : list) {
            if (p.remainingTime > 0) return true;
        }
        return false;
    }

    private static boolean runRRQueue(List<CPUProcess> queue, int quantum,
                                      List<GanttEntryCPU> gantt,
                                      int[] timeRef, int[] busyRef) {
        boolean didWork = false;
        int time = timeRef[0];
        int busy = busyRef[0];

        List<CPUProcess> cycle = new ArrayList<>(queue);
        cycle.sort(Comparator
                .comparingInt((CPUProcess p) -> p.arrivalTime)
                .thenComparingInt(p -> extractPidNumber(p.pid)));

        for (CPUProcess p : cycle) {
            if (p.remainingTime <= 0) continue;
            if (p.arrivalTime > time) continue;

            didWork = true;
            int runTime = Math.min(quantum, p.remainingTime);
            if (p.startTime == -1) p.startTime = time;

            gantt.add(makeGantt(time, time + runTime, p.pid));
            busy += runTime;
            time += runTime;
            p.remainingTime -= runTime;
            if (p.remainingTime == 0) {
                p.completionTime = time;
                p.turnaroundTime = p.completionTime - p.arrivalTime;
                p.waitingTime = p.turnaroundTime - p.burstTime;
                p.responseTime = p.startTime - p.arrivalTime;
            }
        }

        timeRef[0] = time;
        busyRef[0] = busy;
        return didWork;
    }

    private static boolean runFCFSQueue(List<CPUProcess> queue,
                                        List<GanttEntryCPU> gantt,
                                        int[] timeRef, int[] busyRef) {
        boolean didWork = false;
        int time = timeRef[0];
        int busy = busyRef[0];

        queue.sort(Comparator
                .comparingInt((CPUProcess p) -> p.arrivalTime)
                .thenComparingInt(p -> extractPidNumber(p.pid)));

        for (CPUProcess p : queue) {
            if (p.remainingTime <= 0) continue;
            if (time < p.arrivalTime) {
                gantt.add(makeGantt(time, p.arrivalTime, "IDLE"));
                time = p.arrivalTime;
            }
            didWork = true;
            if (p.startTime == -1) p.startTime = time;

            gantt.add(makeGantt(time, time + p.remainingTime, p.pid));
            busy += p.remainingTime;
            time += p.remainingTime;
            p.remainingTime = 0;
            p.completionTime = time;
            p.turnaroundTime = p.completionTime - p.arrivalTime;
            p.waitingTime = p.turnaroundTime - p.burstTime;
            p.responseTime = p.startTime - p.arrivalTime;
        }

        timeRef[0] = time;
        busyRef[0] = busy;
        return didWork;
    }

    private static CPUProcess selectNextSJF(List<CPUProcess> list, int currentTime) {
        return list.stream()
                .filter(p -> p.arrivalTime <= currentTime)
                .min(Comparator
                        .comparingInt((CPUProcess p) -> p.burstTime)
                        .thenComparingInt(p -> p.arrivalTime)
                        .thenComparingInt(p -> extractPidNumber(p.pid)))
                .orElse(null);
    }

    private static GanttEntryCPU makeGantt(int s, int e, String pid) {
        GanttEntryCPU ge = new GanttEntryCPU();
        ge.start = s;
        ge.end = e;
        ge.pid = pid;
        return ge;
    }

    private static ScheduleResultCPU buildResult(List<CPUProcess> procs,
                                                 List<GanttEntryCPU> gantt,
                                                 int busy, int endTime) {
        int totalTime = endTime;
        double sumTAT = 0, sumWT = 0, sumCT = 0, sumRT = 0;
        for (CPUProcess p : procs) {
            sumTAT += p.turnaroundTime;
            sumWT += p.waitingTime;
            sumCT += p.completionTime;
            sumRT += p.responseTime;
        }
        int n = procs.size();
        ScheduleResultCPU res = new ScheduleResultCPU();
        res.processes = procs;
        res.gantt = gantt;
        if (n > 0) {
            res.avgTAT = sumTAT / n;
            res.avgWT = sumWT / n;
            res.avgCT = sumCT / n;
            res.avgRT = sumRT / n;
        }
        if (totalTime > 0) {
            res.utilization = busy * 100.0 / totalTime;
            res.throughput = n * 1.0 / totalTime;
        } else {
            res.utilization = 0.0;
            res.throughput = 0.0;
        }
        return res;
    }

    static int extractPidNumber(String pid) {
        try {
            if (pid.startsWith("P")) return Integer.parseInt(pid.substring(1));
            return Integer.parseInt(pid);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static List<CPUProcess> deepCopy(List<CPUProcess> input) {
        List<CPUProcess> copy = new ArrayList<>();
        for (CPUProcess p : input) {
            CPUProcess c = new CPUProcess();
            c.pid = p.pid;
            c.arrivalTime = p.arrivalTime;
            c.burstTime = p.burstTime;
            c.remainingTime = p.burstTime;
            copy.add(c);
        }
        copy.sort(Comparator
                .comparingInt((CPUProcess p) -> p.arrivalTime)
                .thenComparingInt(p -> extractPidNumber(p.pid)));
        return copy;
    }
}

/* ===========================
   DISK SCHEDULING
   =========================== */

enum DiskAlgorithmType {
    SCAN, LOOK
}

class DiskFrame extends JFrame {

    private DiskAlgorithmType type;

    private JTextField txtCurrentPos, txtTrackSize, txtSeekRate, txtNumReq;
    private JTable tblReq;
    private DefaultTableModel reqModel;
    private JTextArea txtMapping, txtStats;

    public DiskFrame(DiskAlgorithmType type) {
        this.type = type;
        setTitle("Disk Scheduling - " + type.name());
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new ConfirmCloseListener(this, this::resetAll));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("Disk Scheduling: " + type.name()));

        p.add(new JLabel("Current Position:"));
        txtCurrentPos = new JTextField("73", 5);
        p.add(txtCurrentPos);

        p.add(new JLabel("Track Size:"));
        txtTrackSize = new JTextField("200", 5);
        p.add(txtTrackSize);

        p.add(new JLabel("Seek Rate:"));
        txtSeekRate = new JTextField("4", 5);
        p.add(txtSeekRate);

        p.add(new JLabel("Number of Requests [1-12]:"));
        txtNumReq = new JTextField("5", 5);
        p.add(txtNumReq);

        JButton btnSet = new JButton("Set");
        JButton btnReset = new JButton("Reset/Clear");
        JButton btnRun = new JButton("Run " + type.name());

        btnSet.addActionListener(this::onSetReq);
        btnReset.addActionListener(e -> resetAll());
        btnRun.addActionListener(this::onRun);

        p.add(btnSet);
        p.add(btnReset);
        p.add(btnRun);

        return p;
    }

    private JPanel createCenterPanel() {
        JPanel p = new JPanel(new GridLayout(1, 2, 5, 5));

        reqModel = new DefaultTableModel(new Object[]{"Request Location"}, 0);
        tblReq = new JTable(reqModel);
        JScrollPane spReq = new JScrollPane(tblReq);
        spReq.setBorder(new TitledBorder("Requests (Loc 1 .. Loc n)"));
        p.add(spReq);

        txtMapping = new JTextArea(10, 40);
        txtMapping.setEditable(false);
        JScrollPane spMap = new JScrollPane(txtMapping);
        spMap.setBorder(new TitledBorder("Mapping (Head Movement)"));
        p.add(spMap);

        return p;
    }

    private JPanel createBottomPanel() {
        JPanel p = new JPanel(new BorderLayout());
        txtStats = new JTextArea(4, 40);
        txtStats.setEditable(false);
        JScrollPane spStats = new JScrollPane(txtStats);
        spStats.setBorder(new TitledBorder("Results (Total Head Movement, Seek Time)"));
        p.add(spStats, BorderLayout.CENTER);
        return p;
    }

    private void onSetReq(ActionEvent e) {
        int n;
        try {
            n = Integer.parseInt(txtNumReq.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Number of requests must be numeric.");
            return;
        }
        if (n < 1 || n > 12) {
            showError("Number of requests must be between 1 and 12.");
            return;
        }
        reqModel.setRowCount(0);
        for (int i = 0; i < n; i++) {
            reqModel.addRow(new Object[]{0});
        }
    }

    private void resetAll() {
        txtCurrentPos.setText("73");
        txtTrackSize.setText("200");
        txtSeekRate.setText("4");
        txtNumReq.setText("5");
        reqModel.setRowCount(0);
        txtMapping.setText("");
        txtStats.setText("");
    }

    private void onRun(ActionEvent e) {
        int current, trackSize, seekRate;
        try {
            current = Integer.parseInt(txtCurrentPos.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Current position must be numeric.");
            return;
        }
        try {
            trackSize = Integer.parseInt(txtTrackSize.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Track size must be numeric.");
            return;
        }
        try {
            seekRate = Integer.parseInt(txtSeekRate.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Seek rate must be numeric.");
            return;
        }
        if (trackSize <= 0) {
            showError("Track size must be > 0.");
            return;
        }
        if (seekRate <= 0) {
            showError("Seek rate must be > 0.");
            return;
        }
        List<Integer> requests = new ArrayList<>();
        for (int i = 0; i < reqModel.getRowCount(); i++) {
            String val = String.valueOf(reqModel.getValueAt(i, 0)).trim();
            int r;
            try {
                r = Integer.parseInt(val);
            } catch (NumberFormatException ex) {
                showError("Request at row " + (i + 1) + " must be numeric.");
                return;
            }
            if (r < 0 || r >= trackSize) {
                showError("Request at row " + (i + 1) + " must be in [0, " + (trackSize - 1) + "].");
                return;
            }
            requests.add(r);
        }
        if (requests.isEmpty()) {
            showError("At least one request is required.");
            return;
        }

        DiskScheduleResult result;
        if (type == DiskAlgorithmType.SCAN) {
            result = DiskScheduler.scheduleSCAN(current, trackSize, seekRate, requests);
        } else {
            result = DiskScheduler.scheduleLOOK(current, trackSize, seekRate, requests);
        }

        StringBuilder map = new StringBuilder();
        map.append("Head sequence: ").append(result.sequence).append("\n");
        map.append("Movement steps:\n");
        for (int i = 1; i < result.sequence.size(); i++) {
            int from = result.sequence.get(i - 1);
            int to = result.sequence.get(i);
            map.append(String.format("  %3d -> %3d  (|%d|)\n", from, to, Math.abs(to - from)));
        }
        txtMapping.setText(map.toString());

        txtStats.setText("");
        txtStats.append("Total head movement: " + result.totalMovement + " tracks\n");
        txtStats.append("Seek time: " + result.seekTime + " ms (movement * seekRate)\n");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
    }
}

/* ---- Disk scheduling core ---- */

class DiskScheduleResult {
    List<Integer> sequence;
    int totalMovement;
    int seekTime;
}

class DiskScheduler {

    public static DiskScheduleResult scheduleSCAN(int start, int trackSize, int seekRate,
                                                  List<Integer> requests) {
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        for (int r : requests) {
            if (r < start) left.add(r);
            else right.add(r);
        }
        Collections.sort(left);
        Collections.sort(right);

        List<Integer> seq = new ArrayList<>();
        seq.add(start);
        int current = start;
        int movement = 0;

        for (int r : right) {
            movement += Math.abs(r - current);
            current = r;
            seq.add(current);
        }

        if (!left.isEmpty()) {
            if (current != trackSize - 1) {
                movement += Math.abs((trackSize - 1) - current);
                current = trackSize - 1;
                seq.add(current);
            }
            for (int i = left.size() - 1; i >= 0; i--) {
                int r = left.get(i);
                movement += Math.abs(r - current);
                current = r;
                seq.add(current);
            }
        }

        DiskScheduleResult res = new DiskScheduleResult();
        res.sequence = seq;
        res.totalMovement = movement;
        res.seekTime = movement * seekRate;
        return res;
    }

    public static DiskScheduleResult scheduleLOOK(int start, int trackSize, int seekRate,
                                                  List<Integer> requests) {
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        for (int r : requests) {
            if (r < start) left.add(r);
            else right.add(r);
        }
        Collections.sort(left);
        Collections.sort(right);

        List<Integer> seq = new ArrayList<>();
        seq.add(start);
        int current = start;
        int movement = 0;

        for (int r : right) {
            movement += Math.abs(r - current);
            current = r;
            seq.add(current);
        }

        if (!left.isEmpty()) {
            int lastLeft = left.get(left.size() - 1);
            if (current != lastLeft) {
                movement += Math.abs(lastLeft - current);
                current = lastLeft;
                seq.add(current);
            }
            for (int i = left.size() - 2; i >= 0; i--) {
                int r = left.get(i);
                movement += Math.abs(r - current);
                current = r;
                seq.add(current);
            }
        }

        DiskScheduleResult res = new DiskScheduleResult();
        res.sequence = seq;
        res.totalMovement = movement;
        res.seekTime = movement * seekRate;
        return res;
    }
}
