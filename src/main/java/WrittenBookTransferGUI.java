import com.functionlib.Bookprelib;
import com.functionlib.Bookprelib.PreprocessResult;
import com.functionlib.DeepSeekAnalyzer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import com.functionlib.Editlib;
import com.functionlib.FunctionlibApp;
import com.functionlib.TranslateLib;
import com.functionlib.db.BookDao;

import com.writtenbooktransfer.BookTransferService;
import com.writtenbooktransfer.GlobalKeyListener;
import com.writtenbooktransfer.MinecraftBookPreviewDialog;
import com.common.McFunctionParser;
import com.functionlib.FunctionlibApp.BookEntry;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultCaret;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.nio.file.Files;

import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;

import java.util.regex.Pattern;

/**
 * WrittenBookTransfer 系统 Swing 图形界面入口
 */
public class WrittenBookTransferGUI extends JFrame {

    private JDialog currentPreviewDialog = null;

    // 拖拽数据 flavor，传输书籍文件的绝对路径
    private static final DataFlavor BOOK_FLAVOR = DataFlavor.stringFlavor;

    // 主数据库根目录（左表固定显示）
    private File rootDatabaseFolder = null;
    // 当前右侧显示的文件夹
    private File currentSubFolder = null;

    // UI 组件
    private JTextArea logArea;
    private JLabel statusLabel;

    // 主数据库表格（左表）
    private JTable mainBookTable;
    private BookTableModel mainTableModel;
    private BookEntry selectedMainBook = null;

    // 子目录表格（右表）
    private JTable subBookTable;
    private BookTableModel subTableModel;
    private BookEntry selectedSubBook = null;

    // 右键菜单（共用）
    private JPopupMenu tablePopupMenu;
    private JMenuItem openBookItem;
    private JMenuItem deleteBookItem;
    private JMenuItem moveBookItem;
    private JMenuItem translateBookItem;

    // 当前拥有焦点的表格（用于右键菜单等操作）
    private JTable lastFocusedTable = null;

    // 搜索组件
    private JTextField searchField;
    private JComboBox<String> searchModeCombo;
    private JButton searchButton;

    // 左侧目录树组件
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JPanel leftTreePanel;
    private DefaultMutableTreeNode rightClickedNode = null;

    private JScrollPane subScroll;   // 右侧子目录表格的滚动面板，用于动态更新标题

    public WrittenBookTransferGUI() {

        // 设置全局默认字体大小
        Font defaultFont = new Font("宋体", Font.PLAIN, 12);
        UIManager.put("Label.font", defaultFont);
        UIManager.put("Button.font", defaultFont);
        UIManager.put("TextField.font", defaultFont);
        UIManager.put("TextArea.font", defaultFont);
        UIManager.put("Table.font", defaultFont);
        UIManager.put("TableHeader.font", defaultFont);
        UIManager.put("Tree.font", defaultFont);
        UIManager.put("Menu.font", defaultFont);
        UIManager.put("MenuItem.font", defaultFont);
        UIManager.put("ComboBox.font", defaultFont);
        UIManager.put("TabbedPane.font", defaultFont);
        UIManager.put("TitledBorder.font", defaultFont);

        super("WrittenBookTransfer - 成书数据库管理系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 850);
        setLocationRelativeTo(null);

        // 创建表格模型和表格
        mainTableModel = new BookTableModel();
        mainBookTable = new JTable(mainTableModel);
        subTableModel = new BookTableModel();
        subBookTable = new JTable(subTableModel);

        GlobalKeyListener.initGlobalHook();

        initComponents();
        initBookTables();
        initFolderTree();
        initTablePopupMenu();
        redirectSystemOut();  

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                DeepSeekAnalyzer.shutdownExecutor();
            }
        });
    }

    /**
     * 书籍拖拽处理器（负责拖拽源的数据导出）
     */
    private class BookTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTable table = (JTable) c;
            int row = table.getSelectedRow();
            if (row < 0) return null;

            int modelRow = table.convertRowIndexToModel(row);
            BookEntry book = null;
            if (table == mainBookTable) {
                book = mainTableModel.getBookAt(modelRow);
            } else if (table == subBookTable) {
                book = subTableModel.getBookAt(modelRow);
            }
            if (book == null) return null;

            String filePath = book.getFile().getAbsolutePath();
            return new StringSelection(filePath);
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            // 移动后刷新会在放置处理器中处理，这里不做操作
        }
    }

    /**
     * 初始化两个表格的基本设置
     */
    private void initBookTables() {
        // 主表格：支持拖拽源
        mainBookTable.setDragEnabled(true);
        mainBookTable.setTransferHandler(new BookTransferHandler());

        mainBookTable.setFillsViewportHeight(true);
        mainBookTable.setRowHeight(25);
        mainBookTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<BookTableModel> mainSorter = new TableRowSorter<>(mainTableModel);
        mainBookTable.setRowSorter(mainSorter);
        mainBookTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int row = mainBookTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = mainBookTable.convertRowIndexToModel(row);
                    selectedMainBook = mainTableModel.getBookAt(modelRow);
                } else {
                    selectedMainBook = null;
                }
            }
        });
        mainBookTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openBookDetail(selectedMainBook);
                }
            }
        });
        mainBookTable.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                lastFocusedTable = mainBookTable;
            }
        });

        // 子表格：支持拖拽源，但拒绝放置（防止干扰父容器）
        subBookTable.setDragEnabled(true);
        subBookTable.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                JTable table = (JTable) c;
                int row = table.getSelectedRow();
                if (row < 0) return null;
                int modelRow = table.convertRowIndexToModel(row);
                BookEntry book = subTableModel.getBookAt(modelRow);
                if (book == null) return null;
                return new StringSelection(book.getFile().getAbsolutePath());
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return false; // 拒绝放置，确保父组件接收
            }
        });

        subBookTable.setFillsViewportHeight(true);
        subBookTable.setRowHeight(25);
        subBookTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<BookTableModel> subSorter = new TableRowSorter<>(subTableModel);
        subBookTable.setRowSorter(subSorter);
        subBookTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int row = subBookTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = subBookTable.convertRowIndexToModel(row);
                    selectedSubBook = subTableModel.getBookAt(modelRow);
                } else {
                    selectedSubBook = null;
                }
            }
        });
        subBookTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openBookDetail(selectedSubBook);
                }
            }
        });
        subBookTable.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                lastFocusedTable = subBookTable;
            }
        });
    }


    /**
 * 从全部书籍列表中筛选出直接位于根目录下的书籍（用于主数据库视图）
 */
private List<BookEntry> filterRootOnlyBooks(List<BookEntry> allBooks) {
    if (rootDatabaseFolder == null || allBooks == null) {
        return new ArrayList<>();
    }
    List<BookEntry> filtered = new ArrayList<>();
    for (BookEntry book : allBooks) {
        File parent = book.getFile().getParentFile();
        if (parent != null && parent.equals(rootDatabaseFolder)) {
            filtered.add(book);
        }
    }
    return filtered;
}
    /**
     * 安全移动文件（支持跨盘，自动处理已存在目标）
     * @return true 移动成功，false 源和目标相同无需移动
     */
    private boolean safeMoveFile(File source, File targetDir) throws IOException {
        File destFile = new File(targetDir, source.getName());

        if (source.getAbsolutePath().equals(destFile.getAbsolutePath())) {
            System.out.println("源文件与目标位置相同，忽略移动: " + source);
            return false;
        }

        if (destFile.exists()) {
            boolean deleted = destFile.delete();
            if (!deleted) {
                throw new IOException("无法删除已存在的目标文件: " + destFile.getAbsolutePath());
            }
        }

        try {
            java.nio.file.Files.move(source.toPath(), destFile.toPath());
        } catch (java.nio.file.FileSystemException e) {
            java.nio.file.Files.copy(source.toPath(), destFile.toPath());
            boolean deleted = source.delete();
            if (!deleted) {
                System.err.println("警告：源文件复制后无法删除，请手动清理: " + source);
            }
        }
        return true;
    }

    /**
     * 树形图拖拽目标处理器
     */
    private class FolderDropTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(BOOK_FLAVOR)) {
                return false;
            }
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath path = dl.getPath();
            if (path == null) return false;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();
            return userObj instanceof File && ((File) userObj).isDirectory();
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;

            Transferable t = support.getTransferable();
            String bookPath;
            try {
                bookPath = (String) t.getTransferData(BOOK_FLAVOR);
            } catch (Exception e) {
                return false;
            }

            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath path = dl.getPath();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            File targetDir = (File) node.getUserObject();

            if (currentPreviewDialog != null) {
                currentPreviewDialog.dispose();
                currentPreviewDialog = null;
            }

            File bookFile = new File(bookPath);
            String bookTitle = bookFile.getName();
            int confirm = JOptionPane.showConfirmDialog(WrittenBookTransferGUI.this,
                    "确定将《" + bookTitle + "》移动到文件夹 \"" + targetDir.getName() + "\" 吗？",
                    "确认移动", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return false;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    File sourceFile = new File(bookPath);
                    if (!sourceFile.exists()) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "源文件不存在: " + bookPath, "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                        return;
                    }

                    try {
                        boolean moved = safeMoveFile(sourceFile, targetDir);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                FunctionlibApp.refreshDatabase();
                                refreshAll();
                                if (moved) {
                                    statusLabel.setText("已移动到: " + targetDir.getName());
                                } else {
                                    statusLabel.setText("文件已在目标位置，无需移动");
                                }
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "移动失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                }
            }).start();

            return true;
        }
    }

    /**
     * 右侧子目录面板拖拽目标处理器（无确认弹窗）
     */
    private class RightPanelDropTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(BOOK_FLAVOR) && currentSubFolder != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;

            Transferable t = support.getTransferable();
            String bookPath;
            try {
                bookPath = (String) t.getTransferData(BOOK_FLAVOR);
            } catch (Exception e) {
                return false;
            }

            if (currentPreviewDialog != null) {
                currentPreviewDialog.dispose();
                currentPreviewDialog = null;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    File sourceFile = new File(bookPath);
                    if (!sourceFile.exists()) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "源文件不存在: " + bookPath, "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                        return;
                    }

                    try {
                        boolean moved = safeMoveFile(sourceFile, currentSubFolder);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                FunctionlibApp.refreshDatabase();
                                refreshAll();
                                if (moved) {
                                    statusLabel.setText("已移动到右侧子目录");
                                } else {
                                    statusLabel.setText("文件已在目标位置，无需移动");
                                }
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "移动失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                }
            }).start();

            return true;
        }
    }

    /**
     * 主数据库面板拖拽目标处理器（接收从子表格或树拖出的书籍，移动到主数据库根目录）
     */
    private class MainPanelDropTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(BOOK_FLAVOR) && rootDatabaseFolder != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;

            Transferable t = support.getTransferable();
            String bookPath;
            try {
                bookPath = (String) t.getTransferData(BOOK_FLAVOR);
            } catch (Exception e) {
                return false;
            }

            if (currentPreviewDialog != null) {
                currentPreviewDialog.dispose();
                currentPreviewDialog = null;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    File sourceFile = new File(bookPath);
                    if (!sourceFile.exists()) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "源文件不存在: " + bookPath, "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                        return;
                    }

                    try {
                        boolean moved = safeMoveFile(sourceFile, rootDatabaseFolder);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                FunctionlibApp.refreshDatabase();
                                refreshAll();
                                if (moved) {
                                    statusLabel.setText("已移动到主数据库");
                                } else {
                                    statusLabel.setText("文件已在目标位置，无需移动");
                                }
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                        "移动失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                }
            }).start();

            return true;
        }
    }

    /**
     * 根据文件路径查找 BookEntry（保留但拖拽移动中不再使用，仅备其他功能使用）
     */
    private BookEntry findBookEntryByPath(String filePath) {
        File targetFile = new File(filePath);
        if (!targetFile.exists()) return null;

        for (int i = 0; i < mainTableModel.getRowCount(); i++) {
            BookEntry book = mainTableModel.getBookAt(i);
            if (book != null && book.getFile().getAbsolutePath().equals(filePath)) {
                return book;
            }
        }
        for (int i = 0; i < subTableModel.getRowCount(); i++) {
            BookEntry book = subTableModel.getBookAt(i);
            if (book != null && book.getFile().getAbsolutePath().equals(filePath)) {
                return book;
            }
        }
        return parseBookEntryManually(targetFile);
    }

    private BookEntry parseBookEntryManually(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            String title = file.getName().replace(".mcfunction", "");
            String author = "未知";
            String generation = "";
            int pageCount = pages.size();
            int wordCount = 0;
            for (String page : pages) {
                wordCount += page.length();
            }
            String contentHash = FunctionlibApp.computeSha256(String.join("\n", pages));
            return new BookEntry(file.getName(), title, author, generation, file, pageCount, wordCount, contentHash);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 初始化左侧目录树
     */
    private void initFolderTree() {
        rootNode = new DefaultMutableTreeNode("未打开数据库");
        treeModel = new DefaultTreeModel(rootNode);
        folderTree = new JTree(treeModel);
        folderTree.setTransferHandler(new FolderDropTransferHandler());
        folderTree.setRootVisible(true);
        folderTree.setShowsRootHandles(true);

        folderTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObj = node.getUserObject();
                if (userObj instanceof File) {
                    File file = (File) userObj;
                    setText(file.getName());
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                } else {
                    setText(userObj.toString());
                    setIcon(null);
                }
                return this;
            }
        });

        JPopupMenu folderPopupMenu = new JPopupMenu();
        JMenuItem newFolderItem = new JMenuItem("📁 新建子文件夹");
        JMenuItem renameItem = new JMenuItem("✏️ 重命名");
        JMenuItem deleteItem = new JMenuItem("🗑️ 删除");

        newFolderItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (rightClickedNode != null) createSubfolder(rightClickedNode);
            }
        });
        renameItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (rightClickedNode != null) renameFolder(rightClickedNode);
            }
        });
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (rightClickedNode != null) deleteFolder(rightClickedNode);
            }
        });

        folderPopupMenu.add(newFolderItem);
        folderPopupMenu.add(renameItem);
        folderPopupMenu.addSeparator();
        folderPopupMenu.add(deleteItem);

        folderTree.addMouseListener(new MouseAdapter() {
    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            handlePopup(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            handlePopup(e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
            TreePath path = folderTree.getPathForLocation(e.getX(), e.getY());
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
                    Object userObj = treeNode.getUserObject();
                    if (userObj instanceof File) {
                        File dir = (File) userObj;
                        if (dir.isDirectory()) {
                            loadSubFolder(dir);
                        }
                    }
                }
            }
        }
    }

    private void handlePopup(MouseEvent e) {
        int row = folderTree.getRowForLocation(e.getX(), e.getY());
        if (row != -1) {
            folderTree.setSelectionRow(row);
            TreePath path = folderTree.getPathForRow(row);
            Object node = path.getLastPathComponent();
            if (node instanceof DefaultMutableTreeNode) {
                rightClickedNode = (DefaultMutableTreeNode) node;
                Object userObj = rightClickedNode.getUserObject();
                boolean isRealFolder = (userObj instanceof File) && ((File) userObj).isDirectory();
                newFolderItem.setEnabled(isRealFolder || "未打开数据库".equals(userObj));
                renameItem.setEnabled(isRealFolder);
                deleteItem.setEnabled(isRealFolder);
                folderPopupMenu.show(folderTree, e.getX(), e.getY());
            }
        }
    }
});

        JScrollPane treeScroll = new JScrollPane(folderTree);
        leftTreePanel.add(treeScroll, BorderLayout.CENTER);
    }

    // WrittenBookTransferGUI.java

private void buildFolderTree(File rootFolder) {
    DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(rootFolder);
    addSubfolders(newRoot, rootFolder);
    treeModel.setRoot(newRoot);
    rootNode = newRoot;
    folderTree.expandPath(new TreePath(newRoot));
}

private void addSubfolders(DefaultMutableTreeNode parentNode, File folder) {
    File[] subDirs = folder.listFiles(new FileFilter() {
        @Override
        public boolean accept(File file) {
            if (!file.isDirectory()) return false;
            String name = file.getName();
            if (".recycle_bin".equals(name) || ".dbbook".equals(name)) {
                return false;
            }
            return true;
        }
    });

    if (subDirs != null) {
        Arrays.sort(subDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File dir : subDirs) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(dir);
            parentNode.add(childNode);
            addSubfolders(childNode, dir);
        }
    }
}
    private void loadSubFolder(File folder) {
        currentSubFolder = folder;
        statusLabel.setText("正在加载子目录: " + folder.getName());
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("加载子目录: " + folder.getAbsolutePath());
                try {
                    List<BookEntry> books = BookDao.loadAll(folder);
                    if (books.isEmpty()) {
                        FunctionlibApp.scanAndSaveToDatabase(folder);
                        books = BookDao.loadAll(folder);
                    }
                    final List<BookEntry> finalBooks = books;
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            subTableModel.setBooks(finalBooks);
                            subScroll.setBorder(BorderFactory.createTitledBorder(folder.getName()));
                            statusLabel.setText("子目录: " + folder.getName() + "，共 " + finalBooks.size() + " 本书");
                        }
                    });
                } catch (SQLException e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("加载子目录失败");
                            JOptionPane.showMessageDialog(WrittenBookTransferGUI.this, "加载失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }).start();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem openItem = new JMenuItem("打开数据库文件夹");
        JMenuItem exitItem = new JMenuItem("退出");
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openDatabaseFolder();
            }
        });
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        JMenu toolsMenu = new JMenu("工具");
    JMenuItem aiFilterItem = new JMenuItem("AI 全库价值过滤");
    JMenuItem preprocessItem = new JMenuItem("数据库预处理（去重）");
    JMenuItem recycleItem = new JMenuItem("回收站管理");
    JMenuItem extractArchiveItem = new JMenuItem("📦 存档提取成书");

    aiFilterItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            runAiFilter();
        }
    });
    preprocessItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            runPreprocess();
        }
    });
    recycleItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            manageRecycleBin();
        }
    });
    extractArchiveItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            // 在新线程中启动存档提取工具 GUI，避免阻塞 EDT
            new Thread(() -> {
                try {
                    com.bookextractor.MinecraftBookExtractor.main(new String[0]);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                                "启动存档提取工具失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    });

    toolsMenu.add(aiFilterItem);
    toolsMenu.add(preprocessItem);
    toolsMenu.add(recycleItem);
    toolsMenu.addSeparator(); // 可选分隔线，让菜单更清晰
    toolsMenu.add(extractArchiveItem);
    menuBar.add(toolsMenu);
    setJMenuBar(menuBar);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setLayout(new BorderLayout());

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton openFolderButton = new JButton("打开文件夹");
        openFolderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openDatabaseFolder();
            }
        });
        leftButtonPanel.add(openFolderButton);

        JButton aiFilterButton = new JButton("AI 价值过滤");
        aiFilterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runAiFilter();
            }
        });
        leftButtonPanel.add(aiFilterButton);

        JButton recycleButton = new JButton("回收站");
        recycleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manageRecycleBin();
            }
        });
        leftButtonPanel.add(recycleButton);

        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshAll();
            }
        });
        leftButtonPanel.add(refreshButton);

        toolBar.add(leftButtonPanel, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchField = new JTextField(20);
        searchField.setToolTipText("输入关键词，空格分隔");
        searchModeCombo = new JComboBox<String>(new String[]{"标题/作者", "全文检索"});
        searchModeCombo.setToolTipText("选择搜索范围");
        searchButton = new JButton("搜索");
        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });
        searchField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });

        searchPanel.add(new JLabel("搜索："));
        searchPanel.add(searchField);
        searchPanel.add(searchModeCombo);
        searchPanel.add(searchButton);
        toolBar.add(searchPanel, BorderLayout.EAST);

        add(toolBar, BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(220);
        mainSplitPane.setDividerSize(4);

        leftTreePanel = new JPanel(new BorderLayout());
        leftTreePanel.setBorder(BorderFactory.createTitledBorder("目录结构"));
        mainSplitPane.setLeftComponent(leftTreePanel);

        JSplitPane tableSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        tableSplitPane.setResizeWeight(0.5);
        tableSplitPane.setDividerLocation(0.5);
        tableSplitPane.setDividerSize(4);

        JPanel mainTablePanel = new JPanel(new BorderLayout());
        JScrollPane mainScroll = new JScrollPane(mainBookTable);
        mainScroll.setBorder(BorderFactory.createTitledBorder("主数据库"));
        mainScroll.setTransferHandler(new MainPanelDropTransferHandler());
        mainTablePanel.add(mainScroll, BorderLayout.CENTER);
        tableSplitPane.setLeftComponent(mainTablePanel);

        JPanel subTablePanel = new JPanel(new BorderLayout());
        subScroll = new JScrollPane(subBookTable);
        subScroll.setTransferHandler(new RightPanelDropTransferHandler());
        subScroll.setBorder(BorderFactory.createTitledBorder("子目录"));
        subTablePanel.add(subScroll, BorderLayout.CENTER);
        tableSplitPane.setRightComponent(subTablePanel);

        mainSplitPane.setRightComponent(tableSplitPane);
        add(mainSplitPane, BorderLayout.CENTER);

        // 创建一个底部面板，包含日志区域和状态栏
        JPanel bottomPanel = new JPanel(new BorderLayout());

        logArea = new JTextArea(20, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("宋体", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        logScroll.setPreferredSize(new Dimension(800, 200));
        bottomPanel.add(logScroll, BorderLayout.CENTER);

        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void initTablePopupMenu() {
        tablePopupMenu = new JPopupMenu();
        openBookItem = new JMenuItem("打开");
        openBookItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedBookFromFocusedTable();
            }
        });
        tablePopupMenu.add(openBookItem);

        deleteBookItem = new JMenuItem("删除（移入回收站）");
        deleteBookItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedBookFromFocusedTable();
            }
        });
        tablePopupMenu.add(deleteBookItem);

        moveBookItem = new JMenuItem("移动到...");
        moveBookItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelectedBookFromFocusedTable();
            }
        });
        tablePopupMenu.add(moveBookItem);

        translateBookItem = new JMenuItem("翻译为中文");
        translateBookItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                translateSelectedBookFromFocusedTable();
            }
        });
        tablePopupMenu.add(translateBookItem);

        mainBookTable.setComponentPopupMenu(tablePopupMenu);
        subBookTable.setComponentPopupMenu(tablePopupMenu);
    }

    private BookEntry getSelectedBookFromFocusedTable() {
        if (lastFocusedTable == mainBookTable) {
            return selectedMainBook;
        } else if (lastFocusedTable == subBookTable) {
            return selectedSubBook;
        }
        return null;
    }

    private File getCurrentFolderForFocusedTable() {
        if (lastFocusedTable == mainBookTable) {
            return rootDatabaseFolder;
        } else {
            return currentSubFolder;
        }
    }

    private void openSelectedBookFromFocusedTable() {
        BookEntry book = getSelectedBookFromFocusedTable();
        if (book == null) {
            JOptionPane.showMessageDialog(this, "请先选择一本书", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        openBookDetail(book);
    }

    private void deleteSelectedBookFromFocusedTable() {
    BookEntry book = getSelectedBookFromFocusedTable();
    File folder = getCurrentFolderForFocusedTable();
    if (book == null || folder == null) {
        JOptionPane.showMessageDialog(this, "请先选择一本书", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }
    int confirm = JOptionPane.showConfirmDialog(this,
            "确定将《" + book.getTitle() + "》移入回收站吗？\n（其内容相同的副本也将一并移动）",
            "确认删除", JOptionPane.YES_NO_OPTION);
    if (confirm != JOptionPane.YES_OPTION) return;

    new Thread(() -> {
        FunctionlibApp.openFolderFromGUI(folder);
        Editlib.deleteBookByEntry(folder, book, FunctionlibApp.getAllBooks());
        FunctionlibApp.refreshDatabase();
        SwingUtilities.invokeLater(this::refreshAll);
    }).start();
}

        private void moveSelectedBookFromFocusedTable() {
    BookEntry book = getSelectedBookFromFocusedTable();
    File sourceFolder = getCurrentFolderForFocusedTable();
    if (book == null || sourceFolder == null) {
        JOptionPane.showMessageDialog(this, "请先选择一本书", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    JFileChooser chooser = new JFileChooser(sourceFolder);
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("选择目标文件夹");
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File targetFolder = chooser.getSelectedFile();

    new Thread(() -> {
        // 执行移动（内部已处理数据库同步）
        boolean success = Editlib.moveBook(sourceFolder, book, FunctionlibApp.getAllBooks());
        if (success) {
            // 彻底刷新界面和数据库缓存
            SwingUtilities.invokeLater(() -> {
                refreshAll();  // 统一使用全量刷新，确保一致性
                JOptionPane.showMessageDialog(this, "移动成功！", "完成", JOptionPane.INFORMATION_MESSAGE);
            });
        } else {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "移动失败，请查看日志", "错误", JOptionPane.ERROR_MESSAGE);
            });
        }
    }).start();
}
    private void translateSelectedBookFromFocusedTable() {
        BookEntry book = getSelectedBookFromFocusedTable();
        if (book == null) {
            JOptionPane.showMessageDialog(this, "请先选择一本书", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String targetLang = JOptionPane.showInputDialog(this, "请输入目标语言代码（如 zh、en、ja）:", "zh");
        if (targetLang == null || targetLang.trim().isEmpty()) return;
        final String lang = targetLang.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("正在翻译《" + book.getTitle() + "》...");
                try {
                    List<String> pages = McFunctionParser.extractPagesFromFile(book.getFile().toPath());
                    String fullText = String.join("\n\n", pages);
                    String translated = TranslateLib.translateAuto(fullText, lang);
                    if (translated != null) {
                        System.out.println("\n========== 全书译文 ==========");
                        System.out.println(translated);
                        System.out.println("===============================");
                    } else {
                        System.out.println("翻译失败。");
                    }
                } catch (IOException e) {
                    System.err.println("读取书籍失败: " + e.getMessage());
                }
            }
        }).start();
    }

    
    private void openDatabaseFolder() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("选择包含 .mcfunction 文件的文件夹");
    String userDir = System.getProperty("user.dir");
    File defaultDir = new File(userDir);
    if (defaultDir.exists() && defaultDir.isDirectory()) {
        chooser.setCurrentDirectory(defaultDir);
    }
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
    }
    File folder = chooser.getSelectedFile();
    rootDatabaseFolder = folder;
    currentSubFolder = null;

    FunctionlibApp.initRecycleBin(folder);

    // ✅ 最佳位置：扫描之前清理非法目录
    System.out.println("正在清理残留的 .dbbook 目录...");
    FunctionlibApp.cleanInvalidDbbookDirs(folder);
    System.out.println("清理完成。");

    statusLabel.setText("正在加载主数据库: " + folder.getAbsolutePath());

    new Thread(new Runnable() {
        @Override
        public void run() {
            System.out.println("正在加载文件夹: " + folder.getAbsolutePath());
            try {
                List<BookEntry> books = scanFolderDirectly(folder);
                System.out.println("扫描返回书籍数量: " + books.size());
                // ★ 过滤：只保留根目录直属文件
                List<BookEntry> rootOnlyBooks = filterRootOnlyBooks(books);
                final List<BookEntry> finalBooks = rootOnlyBooks;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        mainTableModel.setBooks(finalBooks);
                        subTableModel.setBooks(new ArrayList<BookEntry>());
                        statusLabel.setText("主数据库: " + folder.getName() + "，共 " + finalBooks.size() + " 本书");
                        buildFolderTree(folder);
                        mainBookTable.revalidate();
                        mainBookTable.repaint();
                        JOptionPane.showMessageDialog(WrittenBookTransferGUI.this, 
                                "加载完成，共 " + finalBooks.size() + " 本书", "成功", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("加载失败");
                        JOptionPane.showMessageDialog(WrittenBookTransferGUI.this, 
                                "加载失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }
    }).start();
}

    /**
     * 直接扫描文件夹，解析所有 .mcfunction 文件，返回 BookEntry 列表
     * 注意：为了性能，不再保存到数据库（首次打开只需要显示）
     */
    private List<BookEntry> scanFolderDirectly(File folder) {
        List<BookEntry> books = new ArrayList<BookEntry>();
        File[] mcFiles = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".mcfunction");
            }
        });
        if (mcFiles == null || mcFiles.length == 0) {
            System.out.println("该文件夹中没有 .mcfunction 文件。");
            return books;
        }
        System.out.println("正在扫描文件，请稍候...");
        int total = mcFiles.length;
        int processed = 0;
        for (File file : mcFiles) {
            try {
                BookEntry entry = parseBookEntryDirectly(file);
                if (entry != null) {
                    books.add(entry);
                }
            } catch (Exception e) {
                System.err.println("解析失败: " + file.getName() + " - " + e.getMessage());
            }
            processed++;
        }
        System.out.printf("扫描完成。共找到 %d 个 .mcfunction 文件，解析出 %d 本书籍。\n", total, books.size());
        // 不再保存到数据库，避免性能问题（移动操作时会由 Editlib 更新数据库）
        return books;
    }

    /**
     * 解析单个 .mcfunction 文件为 BookEntry（复用 FunctionlibApp 的解析逻辑）
     */
    private BookEntry parseBookEntryDirectly(File file) throws IOException {
        String content = Files.readString(file.toPath());
        String title = FunctionlibApp.extractField(content, "title");
        String author = FunctionlibApp.extractField(content, "author");
        int generation = FunctionlibApp.extractGeneration(content);
        String generationName = FunctionlibApp.getGenerationName(generation);

        if (title == null || title.isEmpty()) {
            title = file.getName().replace(".mcfunction", "");
        }
        if (author == null || author.isEmpty()) {
            author = "未知";
        }

        List<String> pagesList = McFunctionParser.extractPagesFromFile(file.toPath());
        int pages = pagesList.size();
        int wordCount = 0;
        for (String page : pagesList) {
            wordCount += page.replaceAll("[\\s\\n\\r]+", "").length();
        }

        String fullContent = String.join("\n", pagesList);
        String contentHash = FunctionlibApp.computeSha256(fullContent);
        return new FunctionlibApp.BookEntry(file.getName(), title, author, generationName, file, pages, wordCount, contentHash);
    }

    private void createSubfolder(DefaultMutableTreeNode parentNode) {
        Object userObj = parentNode.getUserObject();
        if ("未打开数据库".equals(userObj)) {
            JOptionPane.showMessageDialog(this, "请先打开数据库文件夹！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!(userObj instanceof File)) return;
        File parentDir = (File) userObj;
        String newName = JOptionPane.showInputDialog(this, "输入新文件夹名称:", "创建子文件夹", JOptionPane.PLAIN_MESSAGE);
        if (newName == null || newName.trim().isEmpty()) return;
        File newDir = new File(parentDir, newName.trim());
        if (newDir.exists()) {
            JOptionPane.showMessageDialog(this, "文件夹已存在！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        boolean created = newDir.mkdir();
        if (created) {
            parentNode.removeAllChildren();
            addSubfolders(parentNode, parentDir);
            treeModel.reload(parentNode);
            folderTree.expandPath(new TreePath(parentNode.getPath()));
            System.out.println("已创建文件夹: " + newDir.getAbsolutePath());
        } else {
            JOptionPane.showMessageDialog(this, "创建文件夹失败！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameFolder(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (!(userObj instanceof File)) return;
        File dir = (File) userObj;
        String newName = JOptionPane.showInputDialog(this, "输入新文件夹名称:", dir.getName());
        if (newName == null || newName.trim().isEmpty() || newName.equals(dir.getName())) return;
        File newDir = new File(dir.getParentFile(), newName.trim());
        if (newDir.exists()) {
            JOptionPane.showMessageDialog(this, "同名文件夹已存在！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        boolean success = dir.renameTo(newDir);
        if (success) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null) {
                node.setUserObject(newDir);
                parent.removeAllChildren();
                addSubfolders(parent, (File) parent.getUserObject());
                treeModel.reload(parent);
                if (rootDatabaseFolder != null && rootDatabaseFolder.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    String newPath = rootDatabaseFolder.getAbsolutePath().replace(dir.getAbsolutePath(), newDir.getAbsolutePath());
                    rootDatabaseFolder = new File(newPath);
                }
                if (currentSubFolder != null && currentSubFolder.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    String newPath = currentSubFolder.getAbsolutePath().replace(dir.getAbsolutePath(), newDir.getAbsolutePath());
                    currentSubFolder = new File(newPath);
                }
            }
            System.out.println("文件夹已重命名: " + dir.getName() + " -> " + newName);
        } else {
            JOptionPane.showMessageDialog(this, "重命名失败！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteFolder(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (!(userObj instanceof File)) return;
        File dir = (File) userObj;

        if (dir.equals(rootDatabaseFolder) || dir.equals(currentSubFolder)) {
            JOptionPane.showMessageDialog(this, "不能删除当前正在使用的数据库目录！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要永久删除文件夹及其所有内容吗？\n" + dir.getAbsolutePath(),
                "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            deleteDirectoryRecursively(dir);
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null) {
                parent.remove(node);
                treeModel.reload(parent);
            }
            System.out.println("已删除文件夹: " + dir.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDirectoryRecursively(File dir) throws IOException {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("无法删除文件: " + file.getAbsolutePath());
                    }
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("无法删除文件夹: " + dir.getAbsolutePath());
        }
    }

    private void runAiFilter() {
        if (rootDatabaseFolder == null) {
            JOptionPane.showMessageDialog(this, "请先打开数据库文件夹！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "AI 价值过滤将评估当前数据库中的所有书籍，耗时较长。\n确定开始吗？",
                "确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("开始 AI 全库价值过滤...");
                try {
                    FunctionlibApp.runAiFilterFromGUI(rootDatabaseFolder);
                    System.out.println("AI 价值过滤完成。");
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            refreshAll();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

private void runPreprocess() {
    if (rootDatabaseFolder == null) {
        JOptionPane.showMessageDialog(this, "请先打开数据库文件夹！", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    int confirm = JOptionPane.showConfirmDialog(this,
            "<html><b>数据库预处理（去重）</b><br>在同一作者内，按内容完全相同的书籍只保留最原始版本。<br>重复副本将被删除。确定开始吗？</html>",
            "确认预处理", JOptionPane.YES_NO_OPTION);
    if (confirm != JOptionPane.YES_OPTION) return;

    System.out.println("========================================");
    System.out.println("开始数据库预处理（去重）");
    System.out.println("根目录: " + rootDatabaseFolder.getAbsolutePath());
    System.out.println("========================================");

    new Thread(() -> {
        try {
            // 阶段1：通过文件系统递归扫描所有 .mcfunction 文件（绕过数据库）
            SwingUtilities.invokeLater(() -> statusLabel.setText("正在扫描文件系统中的所有书籍..."));
            System.out.println("[1/3] 正在递归扫描所有 .mcfunction 文件...");
            long startTime = System.currentTimeMillis();
            List<FunctionlibApp.BookEntry> allBooks = scanAllBooksFromFilesystem(rootDatabaseFolder);
            final int totalBooks = allBooks.size();
            long scanTime = System.currentTimeMillis() - startTime;
            System.out.printf("扫描完成，共找到 %d 本书籍，耗时 %.2f 秒\n", totalBooks, scanTime / 1000.0);
            SwingUtilities.invokeLater(() -> 
                statusLabel.setText("扫描完成，共 " + totalBooks + " 本书，正在分析内容去重...")
            );

            if (totalBooks == 0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "当前数据库中没有书籍。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    statusLabel.setText("就绪");
                });
                return;
            }

            // 按作者分组
            System.out.println("[2/3] 正在按作者分组...");
            Map<String, List<FunctionlibApp.BookEntry>> booksByAuthor = new LinkedHashMap<>();
            for (FunctionlibApp.BookEntry book : allBooks) {
                booksByAuthor.computeIfAbsent(book.getAuthor(), k -> new ArrayList<>()).add(book);
            }
            System.out.printf("分组完成，共 %d 位作者\n", booksByAuthor.size());

            // 阶段2：分析去重（带进度反馈）
            Bookprelib.PreprocessResult result = analyzeDuplicatesWithProgress(booksByAuthor, totalBooks);

            // 阶段3：显示结果对话框
            SwingUtilities.invokeLater(() -> {
                System.out.println("[3/3] 分析完成，准备显示预览对话框");
                System.out.printf("结果统计：保留 %d 本，待删除 %d 本\n", 
                        result.keptBooks.size(), result.removedBooks.size());
                
                if (result.filesToDelete.isEmpty()) {
                    System.out.println("未发现重复副本，无需处理。");
                    JOptionPane.showMessageDialog(this, "未发现重复副本。", "完成", JOptionPane.INFORMATION_MESSAGE);
                    statusLabel.setText("就绪");
                } else {
                    boolean confirmed = showPreprocessPreviewDialog(result);
                    if (confirmed) {
                        System.out.println("用户确认删除，开始执行删除操作...");
                        new Thread(() -> {
                            int deleted = executeDeletionWithLog(result.filesToDelete);
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(this,
                                        String.format("删除完成。成功 %d 个，失败 %d 个。",
                                                deleted, result.filesToDelete.size() - deleted),
                                        "完成", JOptionPane.INFORMATION_MESSAGE);
                                System.out.printf("删除操作结束：成功 %d，失败 %d\n", 
                                        deleted, result.filesToDelete.size() - deleted);
                                refreshAll();
                                statusLabel.setText("预处理完成");
                            });
                        }).start();
                    } else {
                        System.out.println("用户取消了删除操作。");
                        statusLabel.setText("已取消预处理");
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("预处理过程中发生严重错误: " + e.getMessage());
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "预处理过程中发生错误: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("预处理失败");
            });
        }
    }).start();
}

/**
 * 递归扫描文件系统，收集所有 .mcfunction 文件并解析为 BookEntry
 * 跳过回收站和 .dbbook 目录
 */
private List<FunctionlibApp.BookEntry> scanAllBooksFromFilesystem(File rootDir) {
    List<FunctionlibApp.BookEntry> books = new ArrayList<>();
    scanDirectoryRecursively(rootDir, books);
    return books;
}

private void scanDirectoryRecursively(File dir, List<FunctionlibApp.BookEntry> collector) {
    if (dir == null || !dir.isDirectory()) return;
    String dirName = dir.getName();
    if (".recycle_bin".equals(dirName) || ".dbbook".equals(dirName)) {
        System.out.println("[跳过特殊目录] " + dir.getAbsolutePath());
        return;
    }

    // 处理当前目录下的 .mcfunction 文件
    File[] mcFiles = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".mcfunction"));
    if (mcFiles != null) {
        for (File file : mcFiles) {
            try {
                FunctionlibApp.BookEntry entry = parseBookEntryFromFile(file);
                if (entry != null) {
                    collector.add(entry);
                }
            } catch (Exception e) {
                System.err.println("解析失败: " + file.getAbsolutePath() + " - " + e.getMessage());
            }
        }
    }

    // 递归处理子目录
    File[] subDirs = dir.listFiles(File::isDirectory);
    if (subDirs != null) {
        for (File sub : subDirs) {
            scanDirectoryRecursively(sub, collector);
        }
    }
}

/**
 * 直接解析 .mcfunction 文件为 BookEntry（复用 FunctionlibApp 的逻辑）
 */
private FunctionlibApp.BookEntry parseBookEntryFromFile(File file) throws IOException {
    String content = Files.readString(file.toPath());
    String title = FunctionlibApp.extractField(content, "title");
    String author = FunctionlibApp.extractField(content, "author");
    int generation = FunctionlibApp.extractGeneration(content);
    String generationName = FunctionlibApp.getGenerationName(generation);

    if (title == null || title.isEmpty()) {
        title = file.getName().replace(".mcfunction", "");
    }
    if (author == null || author.isEmpty()) {
        author = "未知";
    }

    List<String> pagesList = McFunctionParser.extractPagesFromFile(file.toPath());
    int pages = pagesList.size();
    int wordCount = 0;
    for (String page : pagesList) {
        wordCount += page.replaceAll("[\\s\\n\\r]+", "").length();
    }

    String fullContent = String.join("\n", pagesList);
    String contentHash = FunctionlibApp.computeSha256(fullContent);

    return new FunctionlibApp.BookEntry(
            file.getName(), title, author, generationName,
            file, pages, wordCount, contentHash
    );
}

/**
 * 带进度反馈和详细日志的去重分析方法
 */
private Bookprelib.PreprocessResult analyzeDuplicatesWithProgress(
        Map<String, List<FunctionlibApp.BookEntry>> booksByAuthor,
        int totalBooks) {

    System.out.println("开始内容去重分析...");
    List<FunctionlibApp.BookEntry> kept = new ArrayList<>();
    List<FunctionlibApp.BookEntry> removed = new ArrayList<>();
    List<File> toDelete = new ArrayList<>();

    AtomicInteger processedBooks = new AtomicInteger(0);
    int lastReportedPercent = -1;
    int authorIndex = 0;
    int totalAuthors = booksByAuthor.size();

    for (Map.Entry<String, List<FunctionlibApp.BookEntry>> entry : booksByAuthor.entrySet()) {
        authorIndex++;
        String author = entry.getKey();
        List<FunctionlibApp.BookEntry> authorBooks = entry.getValue();
        System.out.printf("正在处理作者 [%d/%d]: %s (共 %d 本书)\n", 
                authorIndex, totalAuthors, author, authorBooks.size());

        // 按内容哈希分组
        Map<String, List<FunctionlibApp.BookEntry>> contentGroups = new LinkedHashMap<>();
        for (FunctionlibApp.BookEntry book : authorBooks) {
            String content = readFullContent(book.getFile());
            if (content == null) {
                System.err.println("警告：无法读取文件内容，跳过 " + book.getFileName());
                continue;
            }
            String hash = FunctionlibApp.computeSha256(content);
            contentGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(book);

            int current = processedBooks.incrementAndGet();
            int percent = current * 100 / totalBooks;
            if (percent != lastReportedPercent && percent % 5 == 0) {
                lastReportedPercent = percent;
                final int p = percent;
                final int proc = current;
                SwingUtilities.invokeLater(() -> 
                    statusLabel.setText(String.format("正在分析内容去重... %d%% (%d/%d)", p, proc, totalBooks))
                );
                System.out.printf("进度: %d%% (%d/%d)\n", p, proc, totalBooks);
            }
        }

        // 输出当前作者的内容分组情况
        System.out.printf("  作者 %s 的内容分组数: %d\n", author, contentGroups.size());

        // 每组内容保留一本
        for (Map.Entry<String, List<FunctionlibApp.BookEntry>> groupEntry : contentGroups.entrySet()) {
            List<FunctionlibApp.BookEntry> group = groupEntry.getValue();
            if (group.size() == 1) {
                kept.add(group.get(0));
            } else {
                FunctionlibApp.BookEntry keeper = selectKeeperForEntry(group);
                kept.add(keeper);
                for (FunctionlibApp.BookEntry book : group) {
                    if (book != keeper) {
                        removed.add(book);
                        toDelete.add(book.getFile());
                    }
                }
                System.out.printf("  发现重复组：保留《%s》，删除 %d 个副本\n", 
                        keeper.getTitle(), group.size() - 1);
            }
        }
    }

    System.out.printf("去重分析完成。保留 %d 本，待删除 %d 本\n", kept.size(), removed.size());
    SwingUtilities.invokeLater(() -> statusLabel.setText("分析完成，共发现 " + toDelete.size() + " 个重复副本"));
    return new Bookprelib.PreprocessResult(kept, removed, toDelete);
}

/**
 * 执行删除并记录日志
 */
private int executeDeletionWithLog(List<File> filesToDelete) {
    int deletedCount = 0;
    int total = filesToDelete.size();
    System.out.println("开始删除重复文件，共 " + total + " 个...");
    for (int i = 0; i < filesToDelete.size(); i++) {
        File file = filesToDelete.get(i);
        try {
            boolean deleted = Files.deleteIfExists(file.toPath());
            if (deleted) {
                deletedCount++;
                System.out.printf("[%d/%d] 已删除: %s\n", i+1, total, file.getAbsolutePath());
            } else {
                System.err.printf("[%d/%d] 删除失败（文件可能不存在）: %s\n", i+1, total, file.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.printf("[%d/%d] 删除异常: %s - %s\n", i+1, total, file.getAbsolutePath(), e.getMessage());
        }
    }
    System.out.printf("删除操作完成，成功 %d 个，失败 %d 个\n", deletedCount, total - deletedCount);
    return deletedCount;
}

// 读取文件内容（带简单日志）
private String readFullContent(File file) {
    try {
        List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
        return String.join("\n", pages);
    } catch (Exception e) {
        System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
        return null;
    }
}

// 辅助方法：选择保留哪一本（复用 Bookprelib 逻辑）
private FunctionlibApp.BookEntry selectKeeperForEntry(List<FunctionlibApp.BookEntry> group) {
    // 优先：原稿
    List<FunctionlibApp.BookEntry> originals = new ArrayList<>();
    for (FunctionlibApp.BookEntry b : group) {
        if ("原稿".equals(b.getGeneration())) {
            originals.add(b);
        }
    }
    List<FunctionlibApp.BookEntry> candidates = originals.isEmpty() ? group : originals;

    // 其次：文件名不带 _数字 后缀
    Pattern dupPattern = Pattern.compile("_\\d+$");
    List<FunctionlibApp.BookEntry> noSuffix = new ArrayList<>();
    for (FunctionlibApp.BookEntry b : candidates) {
        String base = b.getFileName().replaceFirst("\\.mcfunction$", "");
        if (!dupPattern.matcher(base).find()) {
            noSuffix.add(b);
        }
    }
    if (!noSuffix.isEmpty()) {
        candidates = noSuffix;
    }

    // 最终选文件名长度最小，若相同按字典序
    FunctionlibApp.BookEntry keeper = candidates.get(0);
    for (FunctionlibApp.BookEntry b : candidates) {
        if (b.getFileName().length() < keeper.getFileName().length()) {
            keeper = b;
        } else if (b.getFileName().length() == keeper.getFileName().length()
                && b.getFileName().compareTo(keeper.getFileName()) < 0) {
            keeper = b;
        }
    }
    return keeper;
}

    private void manageRecycleBin() {
    if (rootDatabaseFolder == null) {
        JOptionPane.showMessageDialog(this, "请先打开数据库文件夹！", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }
    FunctionlibApp.openRecycleBinGUI(this, rootDatabaseFolder, this::refreshAll);
}

    private void openBookDetail(BookEntry book) {
        if (book == null) {
            JOptionPane.showMessageDialog(this, "请先选择一本书", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> pages = McFunctionParser.extractPagesFromFile(book.getFile().toPath());
                    StringBuilder contentBuilder = new StringBuilder();
                    for (int i = 0; i < pages.size(); i++) {
                        contentBuilder.append("========== 第 ").append(i + 1).append(" 页 ==========\n");
                        contentBuilder.append(pages.get(i));
                        if (i < pages.size() - 1) contentBuilder.append("\n\n");
                    }
                    String fullContent = contentBuilder.toString();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            showBookDetailDialog(book, fullContent);
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(WrittenBookTransferGUI.this, "读取书籍失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }).start();
    }

    private void showBookDetailDialog(BookEntry book, String content) {
        if (currentPreviewDialog != null) {
            currentPreviewDialog.dispose();
        }

        JDialog dialog = new JDialog(this, "书籍详情", false);
        currentPreviewDialog = dialog;
        dialog.setSize(1100, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 15);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        infoPanel.add(new JLabel("文件名："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(book.getFileName()), gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        infoPanel.add(new JLabel("书名："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(book.getTitle()), gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        infoPanel.add(new JLabel("作者："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(book.getAuthor()), gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        infoPanel.add(new JLabel("世代："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(book.getGeneration()), gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        infoPanel.add(new JLabel("页数："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(String.valueOf(book.getPages())), gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        infoPanel.add(new JLabel("字数："), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(String.valueOf(book.getWordCount())), gbc);

        dialog.add(infoPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextArea originalArea = new JTextArea(content);
        originalArea.setEditable(false);
        originalArea.setFont(new Font("宋体", Font.PLAIN, 14));
        originalArea.setLineWrap(true);
        originalArea.setWrapStyleWord(true);
        JScrollPane leftScroll = new JScrollPane(originalArea);
        leftScroll.setBorder(BorderFactory.createTitledBorder("原文"));
        splitPane.setLeftComponent(leftScroll);

        JTextArea translationArea = new JTextArea();
        translationArea.setEditable(false);
        translationArea.setFont(new Font("宋体", Font.PLAIN, 14));
        translationArea.setLineWrap(true);
        translationArea.setWrapStyleWord(true);
        JScrollPane rightScroll = new JScrollPane(translationArea);
        rightScroll.setBorder(BorderFactory.createTitledBorder("译文"));
        splitPane.setRightComponent(rightScroll);

        dialog.add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton moveButton = new JButton("移动");
        JButton deleteButton = new JButton("放入回收站");
        JButton editButton = new JButton("修改");
        JButton aiTranslateButton = new JButton("AI翻译");
        JButton translateButton = new JButton("翻译");
        JButton aiScoreButton = new JButton("AI评分");
        JButton importToMcButton = new JButton("导入至 Minecraft");
        importToMcButton.addActionListener(e -> {
            importBookToMinecraft(book, content);
        });
        buttonPanel.add(importToMcButton);

        // AI 翻译按钮事件
        aiTranslateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                translationArea.setText("正在调用 Deepseek API 翻译，请稍候...");
                                translationArea.setCaretPosition(0);
                            }
                        });
                        try {
                            String translated = DeepSeekAnalyzer.translateText(content, "zh");
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (translated != null && !translated.isEmpty()) {
                                        translationArea.setText(translated);
                                        rightScroll.setBorder(BorderFactory.createTitledBorder("AI 译文"));
                                    } else {
                                        translationArea.setText("翻译失败，请稍后重试。");
                                    }
                                    translationArea.setCaretPosition(0);
                                }
                            });
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    translationArea.setText("翻译出错：" + ex.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        moveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                File folder = book.getFile().getParentFile();
                moveBookToFolder(book, folder);
            }
        });
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                File folder = book.getFile().getParentFile();
                deleteBookInFolder(book, folder);
            }
        });

        buttonPanel.add(moveButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(editButton);
        buttonPanel.add(aiTranslateButton);
        buttonPanel.add(translateButton);
        buttonPanel.add(aiScoreButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                currentPreviewDialog = null;
            }
            @Override
            public void windowClosing(WindowEvent e) {
                currentPreviewDialog = null;
            }
        });
        dialog.setVisible(true);
    }

    private void moveBookToFolder(BookEntry book, File sourceFolder) {
    JFileChooser chooser = new JFileChooser(sourceFolder);
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("选择目标文件夹");
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File targetFolder = chooser.getSelectedFile();

    new Thread(() -> {
        boolean success = Editlib.moveBook(sourceFolder, book, FunctionlibApp.getAllBooks());
        SwingUtilities.invokeLater(() -> {
            if (success) {
                refreshAll();
                JOptionPane.showMessageDialog(this, "移动成功！", "完成", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "移动失败，请查看日志", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }).start();
}

    private void deleteBookInFolder(BookEntry book, File folder) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定将《" + book.getTitle() + "》移入回收站吗？\n（其内容相同的副本也将一并移动）",
                "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                FunctionlibApp.openFolderFromGUI(folder);
                Editlib.deleteBookByEntry(folder, book, FunctionlibApp.getAllBooks());
                FunctionlibApp.refreshDatabase();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        refreshAll();
                    }
                });
            }
        }).start();
    }

   private void importBookToMinecraft(BookEntry book, String fullContent) {
    // 1. 直接从文件提取纯净页面（避免详情页的页码装饰符）
    List<String> pages;
    try {
        pages = McFunctionParser.extractPagesFromFile(book.getFile().toPath());
    } catch (IOException e) {
        e.printStackTrace();
        pages = new ArrayList<>();
    }
    if (pages.isEmpty()) {
        JOptionPane.showMessageDialog(this, "书籍内容为空，无法导入", "错误", JOptionPane.ERROR_MESSAGE);
        return;
    }
    String plainText = String.join("\n", pages);

    BookTransferService service = new BookTransferService();
    service.setPagesFromContent(plainText);

    // 2. 显示预览对话框
    MinecraftBookPreviewDialog previewDialog = new MinecraftBookPreviewDialog(this, service.getPages(), book.getTitle());
previewDialog.setDefaultFormatAction(() -> {
    List<String> newPages = service.interactiveReformat(previewDialog);
    if (newPages != null) {
        previewDialog.refreshPages(newPages);
        JOptionPane.showMessageDialog(previewDialog,
                "智能排版完成，共 " + newPages.size() + " 页。",
                "完成", JOptionPane.INFORMATION_MESSAGE);
    }
});
previewDialog.setVisible(true);

    if (!previewDialog.isConfirmed()) {
        return; // 用户取消，详情对话框保持打开
    }

    // 3. 关闭详情对话框（用户已确认传输）
    if (currentPreviewDialog != null) {
        currentPreviewDialog.dispose();
        currentPreviewDialog = null;
    }

    // 4. 启动后台传输线程（内部会输出提示并阻塞等待 Ctrl）
    new Thread(() -> {
        service.startTransfer(new BookTransferService.TransferCallback() {
            @Override
            public void onStatus(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(message);
                    logArea.append(message + "\n");
                });
            }

            @Override
            public void onPageStart(int current, int total, String content) {
                SwingUtilities.invokeLater(() -> logArea.append(content + "\n"));
            }

            @Override
            public void onPageComplete(int current) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("已完成第 " + current + " 页"));
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(WrittenBookTransferGUI.this, error, "传输错误", JOptionPane.ERROR_MESSAGE));
            }

            @Override
            public void onFinished() {
                SwingUtilities.invokeLater(() -> statusLabel.setText("传输结束"));
            }
        });
    }).start();
}
    /**
 * 从全部书籍列表中筛选出直接位于根目录下的书籍
 */
private void performSearch() {
    String keywordText = searchField.getText().trim();
    if (keywordText.isEmpty()) {
        JOptionPane.showMessageDialog(this, "请输入搜索关键词", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }
    if (rootDatabaseFolder == null) {
        JOptionPane.showMessageDialog(this, "请先打开数据库文件夹！", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    final String mode = (String) searchModeCombo.getSelectedItem();
    final boolean fullTextSearch = "全文检索".equals(mode);
    final String[] keywords = keywordText.split("\\s+");

    // 从当前主表格模型中获取所有显示的书籍（这就是实际加载的数据）
    List<BookEntry> sourceBooks = new ArrayList<>();
    for (int i = 0; i < mainTableModel.getRowCount(); i++) {
        sourceBooks.add(mainTableModel.getBookAt(i));
    }

    // 如果当前右侧子目录打开且正在显示，也可以搜索右侧表格，或者统一搜索整个数据库
    // 为了简单，这里只搜索主表格的书籍（根目录直属文件），如需搜索全库请替换数据源
    final List<BookEntry> allBooks = sourceBooks;
    final List<SearchResult> results = new ArrayList<>();

    new Thread(() -> {
        for (BookEntry book : allBooks) {
            boolean titleMatch = matchesKeywords(book.getTitle(), keywords);
            boolean authorMatch = matchesKeywords(book.getAuthor(), keywords);

            if (titleMatch || authorMatch) {
                StringBuilder matchedFields = new StringBuilder();
                if (titleMatch) matchedFields.append("书名 ");
                if (authorMatch) matchedFields.append("作者 ");
                String snippet = fullTextSearch ? "" : "— 仅匹配元数据 —";
                results.add(new SearchResult(book, matchedFields.toString().trim(), snippet));
                continue;
            }

            if (fullTextSearch) {
                try {
                    String fullText = Files.readString(book.getFile().toPath(), StandardCharsets.UTF_8);
                    if (matchesKeywords(fullText, keywords)) {
                        String snippet = generateSnippet(fullText, keywords, 100);
                        results.add(new SearchResult(book, "内容", snippet));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        SwingUtilities.invokeLater(() -> showSearchResults(keywordText, results));
    }).start();
}

    private boolean matchesKeywords(String text, String[] keywords) {
    if (text == null) return false;
    String lowerText = text.toLowerCase();
    for (String kw : keywords) {
        if (lowerText.contains(kw.toLowerCase())) return true;
    }
    return false;
}

/**
 * 递归收集所有需要更新数据库的文件夹（排除 .recycle_bin 及其子文件夹）
 * @param folder 起始文件夹
 * @param result 收集结果的列表
 * @param visited 用于防止循环链接的路径集合
 */
private void collectAllFolders(File folder, List<File> result, Set<String> visited) {
    if (folder == null || !folder.isDirectory()) {
        return;
    }
    // ✅ 跳过回收站和数据库目录
    String name = folder.getName();
    if (".recycle_bin".equals(name) || ".dbbook".equals(name)) {
        return;
    }

    String canonicalPath;
    try {
        canonicalPath = folder.getCanonicalPath();
    } catch (IOException e) {
        System.err.println("无法获取规范路径，跳过: " + folder.getAbsolutePath());
        return;
    }
    if (visited.contains(canonicalPath)) {
        return;
    }
    visited.add(canonicalPath);
    result.add(folder);

    File[] subDirs = folder.listFiles(File::isDirectory);
    if (subDirs != null) {
        for (File sub : subDirs) {
            collectAllFolders(sub, result, visited);
        }
    }
}


  private void refreshAll() {
    System.out.println("【refreshAll】开始刷新...");
    if (rootDatabaseFolder == null) {
        statusLabel.setText("请先打开数据库文件夹");
        return;
    }
    statusLabel.setText("正在扫描更新所有目录的数据库...");
    new Thread(() -> {
        try {
            // 1. 收集所有需要更新的文件夹（排除回收站）
            List<File> foldersToUpdate = new ArrayList<>();
            collectAllFolders(rootDatabaseFolder, foldersToUpdate, new HashSet<>());
            System.out.println("共找到 " + foldersToUpdate.size() + " 个文件夹需要更新数据库。");
            
            // 2. 逐一更新每个文件夹的数据库（先删除旧 .dbbook，强制重新扫描）
            int processed = 0;
            for (File folder : foldersToUpdate) {
                System.out.println("正在更新数据库 (" + (++processed) + "/" + foldersToUpdate.size() + "): " + folder.getAbsolutePath());
                // 强制删除旧的数据库文件，确保重新扫描文件系统
                File dbFile = new File(folder, ".dbbook.mv.db");
                if (dbFile.exists()) {
                    boolean deleted = dbFile.delete();
                    if (deleted) {
                        System.out.println("  已删除旧数据库文件: " + dbFile.getAbsolutePath());
                    } else {
                        System.err.println("  删除旧数据库失败: " + dbFile.getAbsolutePath());
                    }
                }
                // 调用扫描并保存（此时会重建 .dbbook）
                FunctionlibApp.scanAndSaveToDatabase(folder);
            }
            
            // 3. 更新完成后，刷新表格显示
            SwingUtilities.invokeLater(() -> {
                try {
                    long t0 = System.currentTimeMillis();
        statusLabel.setText("正在准备表格数据...");
                    // 刷新主表格（仅显示根目录直属文件）
                    List<BookEntry> mainBooks = BookDao.loadAll(rootDatabaseFolder);
                     long t1 = System.currentTimeMillis();
                    List<BookEntry> rootOnlyBooks = filterRootOnlyBooks(mainBooks);
                     long t2 = System.currentTimeMillis();
                    mainTableModel.setBooks(rootOnlyBooks);
                     long t3 = System.currentTimeMillis();
                    mainBookTable.clearSelection();
                    
                    // 如果右侧子目录已打开，也刷新子表格
                    if (currentSubFolder != null) {
                        List<BookEntry> subBooks = BookDao.loadAll(currentSubFolder);
                        subTableModel.setBooks(subBooks);
                        subBookTable.clearSelection();
                    }
                    
                    statusLabel.setText("刷新完成，共更新 " + foldersToUpdate.size() + " 个文件夹");
                    System.out.println("【refreshAll】刷新完成，主表书籍数：" + rootOnlyBooks.size());
                } catch (SQLException e) {
                    e.printStackTrace();
                    statusLabel.setText("刷新表格失败");
                    JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                            "刷新表格失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("扫描更新失败");
                JOptionPane.showMessageDialog(WrittenBookTransferGUI.this,
                        "扫描更新失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            });
        }
    }).start();
}

private PreprocessResult analyzeDuplicates(Map<String, List<FunctionlibApp.BookEntry>> booksByAuthor) {
    List<FunctionlibApp.BookEntry> kept = new ArrayList<>();
    List<FunctionlibApp.BookEntry> removed = new ArrayList<>();
    List<File> toDelete = new ArrayList<>();

    for (Map.Entry<String, List<FunctionlibApp.BookEntry>> entry : booksByAuthor.entrySet()) {
        List<FunctionlibApp.BookEntry> authorBooks = entry.getValue();
        
        // 按内容哈希分组（内容相同的书籍为一组）
        Map<String, List<FunctionlibApp.BookEntry>> contentGroups = new LinkedHashMap<>();
        for (FunctionlibApp.BookEntry book : authorBooks) {
            String content = readFullContent(book.getFile());
            if (content == null) continue;
            // 使用 SHA-256 作为内容标识（也可直接使用字符串，但哈希更高效）
            String hash = FunctionlibApp.computeSha256(content);
            contentGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(book);
        }

        for (List<FunctionlibApp.BookEntry> group : contentGroups.values()) {
            if (group.size() == 1) {
                kept.add(group.get(0));
            } else {
                FunctionlibApp.BookEntry keeper = selectKeeper(group);
                kept.add(keeper);
                for (FunctionlibApp.BookEntry book : group) {
                    if (book != keeper) {
                        removed.add(book);
                        toDelete.add(book.getFile());
                    }
                }
            }
        }
    }
    return new PreprocessResult(kept, removed, toDelete);
}


// 选择保留哪一本（与 Bookprelib 逻辑一致）
private FunctionlibApp.BookEntry selectKeeper(List<FunctionlibApp.BookEntry> group) {
    // 优先：原稿
    List<FunctionlibApp.BookEntry> originals = new ArrayList<>();
    for (FunctionlibApp.BookEntry b : group) {
        if ("原稿".equals(b.getGeneration())) {
            originals.add(b);
        }
    }
    List<FunctionlibApp.BookEntry> candidates = originals.isEmpty() ? group : originals;

    // 其次：文件名不带 _数字 后缀
    List<FunctionlibApp.BookEntry> noSuffix = new ArrayList<>();
    Pattern dupPattern = Pattern.compile("_\\d+$");
    for (FunctionlibApp.BookEntry b : candidates) {
        String base = b.getFileName().replaceFirst("\\.mcfunction$", "");
        Matcher m = dupPattern.matcher(base);
        if (!m.find()) {
            noSuffix.add(b);
        }
    }
    if (!noSuffix.isEmpty()) {
        candidates = noSuffix;
    }

    // 最终选文件名长度最小，若相同按字典序
    FunctionlibApp.BookEntry keeper = candidates.get(0);
    for (FunctionlibApp.BookEntry b : candidates) {
        if (b.getFileName().length() < keeper.getFileName().length()) {
            keeper = b;
        } else if (b.getFileName().length() == keeper.getFileName().length() 
                && b.getFileName().compareTo(keeper.getFileName()) < 0) {
            keeper = b;
        }
    }
    return keeper;
}

private int executeDeletion(List<File> filesToDelete) {
    int deletedCount = 0;
    for (File file : filesToDelete) {
        try {
            boolean deleted = Files.deleteIfExists(file.toPath());
            if (deleted) deletedCount++;
        } catch (IOException e) {
            System.err.println("删除失败: " + file.getName() + " - " + e.getMessage());
        }
    }
    return deletedCount;
}

private boolean showPreprocessPreviewDialog(Bookprelib.PreprocessResult result) {
    JDialog dialog = new JDialog(this, "预处理预览 - 重复副本删除确认", true);
    dialog.setSize(1000, 600);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());

    // 顶部说明
    JLabel infoLabel = new JLabel("<html><b>以下为去重分析结果：</b> 共发现 " 
            + result.filesToDelete.size() + " 个重复副本。请确认是否删除？</html>");
    infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    dialog.add(infoLabel, BorderLayout.NORTH);

    // 使用选项卡分别展示保留和删除的书籍
    JTabbedPane tabbedPane = new JTabbedPane();

    // 保留书籍表格
    JTable keptTable = createBookTable(result.keptBooks);
    JScrollPane keptScroll = new JScrollPane(keptTable);
    keptScroll.setBorder(BorderFactory.createTitledBorder("保留的书籍（原始版本）"));
    tabbedPane.addTab("保留 (" + result.keptBooks.size() + ")", keptScroll);

    // 待删除书籍表格
    JTable removedTable = createBookTable(result.removedBooks);
    JScrollPane removedScroll = new JScrollPane(removedTable);
    removedScroll.setBorder(BorderFactory.createTitledBorder("待删除的书籍（重复副本）"));
    tabbedPane.addTab("待删除 (" + result.removedBooks.size() + ")", removedScroll);

    dialog.add(tabbedPane, BorderLayout.CENTER);

    // 底部按钮
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
    JButton confirmButton = new JButton("确认删除");
    JButton cancelButton = new JButton("取消");

    final boolean[] userChoice = {false};
    confirmButton.addActionListener(e -> {
        userChoice[0] = true;
        dialog.dispose();
    });
    cancelButton.addActionListener(e -> {
        userChoice[0] = false;
        dialog.dispose();
    });

    buttonPanel.add(confirmButton);
    buttonPanel.add(cancelButton);
    dialog.add(buttonPanel, BorderLayout.SOUTH);

    dialog.setVisible(true);
    return userChoice[0];
}

// 辅助方法：根据书籍列表创建 JTable
private JTable createBookTable(List<FunctionlibApp.BookEntry> books) {
    String[] columnNames = {"书名", "作者", "世代", "文件名"};
    Object[][] data = new Object[books.size()][4];
    for (int i = 0; i < books.size(); i++) {
        FunctionlibApp.BookEntry b = books.get(i);
        data[i][0] = b.getTitle();
        data[i][1] = b.getAuthor();
        data[i][2] = b.getGeneration();
        data[i][3] = b.getFileName();
    }
    JTable table = new JTable(data, columnNames);
    table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(200);
    table.getColumnModel().getColumn(1).setPreferredWidth(120);
    table.getColumnModel().getColumn(2).setPreferredWidth(80);
    table.getColumnModel().getColumn(3).setPreferredWidth(250);
    return table;
}

    private String generateSnippet(String text, String[] keywords, int maxLength) {
        if (text == null || text.isEmpty()) return "";
        String lowerText = text.toLowerCase();
        int firstPos = -1;
        for (String kw : keywords) {
            int pos = lowerText.indexOf(kw.toLowerCase());
            if (pos >= 0 && (firstPos == -1 || pos < firstPos)) firstPos = pos;
        }
        if (firstPos < 0) firstPos = 0;
        int start = Math.max(0, firstPos - maxLength / 2);
        int end = Math.min(text.length(), start + maxLength);
        String snippet = text.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < text.length()) snippet = snippet + "...";
        return snippet;
    }

    private void showSearchResults(String keyword, List<SearchResult> results) {
        JDialog dialog = new JDialog(this, "搜索结果：'" + keyword + "'", false);
        dialog.setSize(1000, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel(" 共找到 " + results.size() + " 条结果");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(titleLabel, BorderLayout.NORTH);

        SearchResultTableModel resultModel = new SearchResultTableModel(results, keyword);
        JTable resultTable = new JTable(resultModel);
        resultTable.setRowHeight(40);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(500);

        resultTable.setDefaultRenderer(Object.class, new HighlightRenderer(keyword));

        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = resultTable.getSelectedRow();
                    if (row >= 0) {
                        SearchResult sr = resultModel.getResultAt(row);
                        dialog.dispose();
                        openBookDetail(sr.book);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultTable);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void redirectSystemOut() {
        OutputStream out = new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            @Override
            public void write(int b) {
                buffer.write(b);
            }
            @Override
            public void flush() throws IOException {
                final String text = buffer.toString(StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        logArea.append(text);
                    }
                });
                buffer.reset();
            }
        };
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static class BookTableModel extends AbstractTableModel {
        private final String[] columnNames = {"书名", "作者", "世代", "页数", "字数"};
        private List<BookEntry> books = new ArrayList<BookEntry>();

        public void setBooks(List<BookEntry> books) {
            this.books = books != null ? books : new ArrayList<BookEntry>();
            fireTableDataChanged();
        }

        public BookEntry getBookAt(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < books.size()) return books.get(rowIndex);
            return null;
        }

        @Override
        public int getRowCount() { return books.size(); }
        @Override
        public int getColumnCount() { return columnNames.length; }
        @Override
        public String getColumnName(int column) { return columnNames[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BookEntry book = books.get(rowIndex);
            switch (columnIndex) {
                case 0: return book.getTitle();
                case 1: return book.getAuthor();
                case 2: return book.getGeneration();
                case 3: return book.getPages();
                case 4: return book.getWordCount();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3 || columnIndex == 4) return Integer.class;
            return String.class;
        }
    }

    private static class SearchResult {
        BookEntry book;
        String matchField;
        String snippet;
        SearchResult(BookEntry book, String matchField, String snippet) {
            this.book = book;
            this.matchField = matchField;
            this.snippet = snippet;
        }
    }

    private static class SearchResultTableModel extends AbstractTableModel {
        private final String[] columnNames = {"书名", "作者", "匹配字段", "内容片段"};
        private List<SearchResult> results;
        private String keyword;

        SearchResultTableModel(List<SearchResult> results, String keyword) {
            this.results = results;
            this.keyword = keyword;
        }

        public SearchResult getResultAt(int row) { return results.get(row); }

        @Override
        public int getRowCount() { return results.size(); }
        @Override
        public int getColumnCount() { return 4; }
        @Override
        public String getColumnName(int column) { return columnNames[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SearchResult sr = results.get(rowIndex);
            switch (columnIndex) {
                case 0: return sr.book.getTitle();
                case 1: return sr.book.getAuthor();
                case 2: return sr.matchField;
                case 3: return sr.snippet.isEmpty() ? "（匹配标题/作者）" : sr.snippet;
                default: return null;
            }
        }
    }

    private static class HighlightRenderer extends DefaultTableCellRenderer {
        private final java.util.regex.Pattern pattern;
        HighlightRenderer(String keyword) {
            String[] words = keyword.split("\\s+");
            String regex = String.join("|", words);
            this.pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof String && !pattern.pattern().isEmpty()) {
                String text = (String) value;
                Matcher matcher = pattern.matcher(text);
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, "<span style='background-color:yellow;'>" + matcher.group() + "</span>");
                }
                matcher.appendTail(sb);
                setText("<html>" + sb.toString() + "</html>");
            }
            return c;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new WrittenBookTransferGUI().setVisible(true);
            }
        });
    }
}