
import com.booktypesetting.TypesettingApp;
import com.bookextractor.BookExtractorApp;
import com.bookexport.BookExportApp;
import com.bookcreator.BookCreatorApp;
import com.functionlib.FunctionlibApp;   // 新增

import java.util.Scanner;

/**
 * WrittenBookTransfer 系统总入口
 * 提供统一的命令行菜单，调度六个子模块。
 */
public class Main {

    private static final String VERSION = "v0.0.1";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("========================================");
        System.out.println("     WrittenBookTransfer 系统 " + VERSION);
        System.out.println("========================================");
        System.out.println("输入 'help' 查看帮助，输入 '0' 退出系统。");

        while (true) {
            System.out.println("\n请选择功能模块：");
            System.out.println("  1. 成书.mcfunction文件复制进入Minecraft (writtenbooktransfer)");
            System.out.println("  2. 文本排版工具 (booktypesetting)<开发中>");
            System.out.println("  3. 存档自动提取成书 (bookextractor)");
            System.out.println("  4. .mcfunction成书导出为.txt (bookexport)");
            System.out.println("  5. 游戏内书与笔转化为.txt (bookcreator)");
            System.out.println("  6. .mcfunction数据库处理模块 (functionlib)"); 
            System.out.println("  0. 退出系统");
            System.out.print("请输入选项 (0-6) 或 'help': ");  

            String input = scanner.nextLine().trim();

            // 处理 help 命令
            if (input.equalsIgnoreCase("help")) {
                showHelp();
                continue;
            }

            // 处理数字选项
            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("\n无效输入，请输入数字选项或 'help'。");
                continue;
            }

            switch (choice) {
                case 1:
                    System.out.println("\n正在启动传输模块...\n");

                    break;
                case 2:
                    System.out.println("\n正在启动排版模块...\n");
                    TypesettingApp.main(new String[0]);
                    break;
                case 3:
                    System.out.println("\n正在启动提取模块...\n");
                    BookExtractorApp.main(new String[0]);
                    break;
                case 4:
                    System.out.println("\n正在启动导出模块...\n");
                    BookExportApp.main(new String[0]);
                    break;
                case 5:
                    System.out.println("\n正在启动手动创建模块...\n");
                    BookCreatorApp.main(new String[0]);
                    break;
                case 6:   // 新增
                    System.out.println("\n正在启动数据库处理模块...\n");
                    FunctionlibApp.main(new String[0]);
                    break;
                case 0:
                    System.out.println("\n感谢使用 WrittenBookTransfer，再见！");
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("\n无效选项，请输入 0-6 之间的数字。");
                    break;
            }
        }
    }

    /**
     * 显示系统帮助信息
     */
    private static void showHelp() {
        System.out.println("\n========================================");
        System.out.println("     WrittenBookTransfer 帮助文档");
        System.out.println("========================================");
        System.out.println("版本: " + VERSION);
        System.out.println("描述: Minecraft 成书工具箱");
        System.out.println("      覆盖提取、创建、导出、排版、自动传输、数据库管理等功能。");
        System.out.println();
        System.out.println("\n【项目包结构】");
        System.out.println("src/main/java/");
        System.out.println("├── Main.java                     // 总入口（默认包）");
        System.out.println("└── com/");
        System.out.println("    ├── common/");
        System.out.println("    │   └── McFunctionParser.java // 公共解析工具");
        System.out.println("    ├── writtenbooktransfer/");
        System.out.println("    │   ├── TransferApp.java      // 传输模块入口");
        System.out.println("    │   ├── InputSimulator.java   // 鼠标键盘模拟");
        System.out.println("    │   └── GlobalKeyListener.java// 全局热键监听");
        System.out.println("    ├── booktypesetting/");
        System.out.println("    │   ├── TypesettingApp.java   // 排版模块入口");
        System.out.println("    │   ├── BookConfig.java       // 排版参数配置");
        System.out.println("    │   └── TextFormatter.java    // 分行分页算法");
        System.out.println("    ├── bookextractor/");
        System.out.println("    │   ├── BookExtractorApp.java // 提取模块入口");
        System.out.println("    │   ├── ExtractorFrame.java   // 提取工具 GUI 界面");
        System.out.println("    │   └── MinecraftBookExtractor.java // 核心提取逻辑");
        System.out.println("    ├── bookexport/");
        System.out.println("    │   ├── BookExportApp.java    // 导出模块入口");
        System.out.println("    │   └── McFunctionExporter.java// 导出核心逻辑");
        System.out.println("    ├── bookcreator/");
        System.out.println("    │   └── BookCreatorApp.java   // 手动创建模块入口");
        System.out.println("    └── functionlib/");            // 新增
        System.out.println("        └── FunctionlibApp.java   // 数据库处理模块入口");
        System.out.println();
        System.out.println("\n【模块依赖关系】");
        System.out.println("Main");
        System.out.println(" ├── TransferApp ──────┬── InputSimulator");
        System.out.println(" │                      ├── GlobalKeyListener");
        System.out.println(" │                      ├── McFunctionParser (common)");
        System.out.println(" │                      └── TextFormatter (booktypesetting)");
        System.out.println(" ├── TypesettingApp ────┬── BookConfig");
        System.out.println(" │                      └── TextFormatter");
        System.out.println(" ├── BookExtractorApp ──┬── ExtractorFrame");
        System.out.println(" │                      └── MinecraftBookExtractor (内含 NBT)");
        System.out.println(" ├── BookExportApp ─────┬── McFunctionExporter");
        System.out.println(" │                      └── McFunctionParser (common)");
        System.out.println(" ├── BookCreatorApp");
        System.out.println(" └── FunctionlibApp");   // 新增
        System.out.println("\n【模块概览】");
        System.out.println("  1. 书本自动传输 (writtenbooktransfer)");
        System.out.println("     - 入口类: com.writtenbooktransfer.TransferApp");
        System.out.println("     - 功能: 加载 .mcfunction 或 .txt 文件，模拟键盘将内容逐页输入游戏。");
        System.out.println("     - 支持: 手动重新排版、设置鼠标偏移/页数限制、ESC 安全中止。");
        System.out.println("     - 依赖: InputSimulator (鼠标键盘模拟)、GlobalKeyListener (全局热键)");
        System.out.println();
        System.out.println("  2. 文本排版工具 (booktypesetting)");
        System.out.println("     - 入口类: com.booktypesetting.TypesettingApp");
        System.out.println("     - 功能: 独立的高级排版工作台（占位，核心排版算法已在传输模块中调用）。");
        System.out.println("     - 核心类: BookConfig (排版参数)、TextFormatter (分行分页算法)。");
        System.out.println();
        System.out.println("  3. 存档成书提取 (bookextractor)");
        System.out.println("     - 入口类: com.bookextractor.BookExtractorApp");
        System.out.println("     - 功能: 从 Minecraft 存档的 .mca 区域文件中扫描箱子、讲台、掉落物等，");
        System.out.println("             提取所有成书并生成对应的 give 命令 .mcfunction 文件。");
        System.out.println("     - 支持: GUI 模式 (ExtractorFrame) 与命令行无头模式。");
        System.out.println("     - 核心引擎: MinecraftBookExtractor (内含完整 NBT 解析器)。");
        System.out.println();
        System.out.println("  4. 成书导出为文本 (bookexport)");
        System.out.println("     - 入口类: com.bookexport.BookExportApp");
        System.out.println("     - 功能: 将 .mcfunction 中的成书内容解析并合并为纯文本 .txt，便于校对或存档。");
        System.out.println("     - 核心类: McFunctionExporter (调用公共解析器 McFunctionParser)。");
        System.out.println();
        System.out.println("  5. 手动创建成书文件 (bookcreator)");
        System.out.println("     - 入口类: com.bookcreator.BookCreatorApp");
        System.out.println("     - 功能: 交互式逐页输入文本，实时预览，最终生成标准 .mcfunction 文件。");
        System.out.println();
        System.out.println("  6. .mcfunction数据库处理与分类 (functionlib)");
        System.out.println("     - 入口类: com.functionlib.FunctionlibApp");
        System.out.println("     - 功能: 批量处理、分类、索引大量的 .mcfunction 文件，提供数据库式管理。");
        System.out.println();
        System.out.println("【公共工具】");
        System.out.println("  • com.common.McFunctionParser");
        System.out.println("    - 提供静态方法从 .mcfunction 提取书本页面文本，被传输/导出/创建模块复用。");
        System.out.println();
        System.out.println("【工作流建议】");
        System.out.println("  提取 (3) → 导出校对 (4) 或 手动创建 (5) → 传输排版 (1) → 游戏内自动输入");
        System.out.println("  数据库管理 (6) 可对大量 .mcfunction 进行预处理和分类。");
        System.out.println();
        System.out.println("【注意事项】");
        System.out.println("  • 传输模块需以管理员/root 权限运行（全局钩子需要）。");
        System.out.println("  • 传输前请将游戏窗口置于前台，并将鼠标悬停在书本翻页按钮上。");
        System.out.println("  • 首次使用传输模块时，建议先用预览模式或调试打印检查排版。");
        System.out.println("========================================\n");
    }
}