package dev.msaad.burp;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom Burp Suite tab displaying broken link results in a sortable JTable.
 * Inspired by Logger++ and Autorize UI patterns.
 */
public class ResultsPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "#", "Platform", "Status", "Broken URL", "Found On", "Reason", "Time"
    };

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private int rowCount = 0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ResultsPanel() {
        setLayout(new BorderLayout(0, 5));

        // ── Table ──
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only
            }
        };
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column widths
        table.getColumnModel().getColumn(0).setMaxWidth(50); // #
        table.getColumnModel().getColumn(1).setMaxWidth(100); // Platform
        table.getColumnModel().getColumn(2).setMaxWidth(60); // Status
        table.getColumnModel().getColumn(6).setMaxWidth(80); // Time

        JScrollPane scrollPane = new JScrollPane(table);

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        statusLabel = new JLabel("0 broken links found");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        JButton clearBtn = new JButton("Clear All");
        clearBtn.addActionListener(this::onClear);

        JButton exportBtn = new JButton("Export CSV");
        exportBtn.addActionListener(this::onExport);

        toolbar.add(statusLabel);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(clearBtn);
        toolbar.add(exportBtn);

        // ── Layout ──
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Adds a broken link finding to the results table.
     * Thread-safe — can be called from any thread.
     */
    public void addResult(Platform platform, int statusCode,
            String brokenUrl, String foundOn, String reason) {
        SwingUtilities.invokeLater(() -> {
            rowCount++;
            tableModel.addRow(new Object[] {
                    rowCount,
                    platform.getDisplayName(),
                    statusCode,
                    brokenUrl,
                    foundOn,
                    reason,
                    LocalDateTime.now().format(TIME_FMT)
            });
            statusLabel.setText(rowCount + " broken link" + (rowCount != 1 ? "s" : "") + " found");
        });
    }

    /**
     * Returns the current number of results.
     */
    public int getResultCount() {
        return rowCount;
    }

    private void onClear(ActionEvent e) {
        tableModel.setRowCount(0);
        rowCount = 0;
        statusLabel.setText("0 broken links found");
    }

    private void onExport(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("broken_links.csv"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
            return;

        File file = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            // Header
            pw.println(String.join(",", COLUMN_NAMES));
            // Rows
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < tableModel.getColumnCount(); j++) {
                    if (j > 0)
                        sb.append(",");
                    Object val = tableModel.getValueAt(i, j);
                    String cell = val != null ? val.toString() : "";
                    // Escape commas and quotes
                    if (cell.contains(",") || cell.contains("\"")) {
                        cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                    }
                    sb.append(cell);
                }
                pw.println(sb);
            }
            JOptionPane.showMessageDialog(this,
                    "Exported " + tableModel.getRowCount() + " results to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
