package tconstruct.tools.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mantle.books.BookData;
import mantle.client.MProxyClient;
import mantle.client.gui.GuiManual;
import mantle.client.gui.TurnPageButton;
import mantle.client.pages.BookPage;
import tconstruct.TConstruct;

/**
 * TConstruct 手册界面。
 *
 * <p>该类复用 Mantle 的书籍数据、页面类型和贴图资源，并在本类中实现打开动画、自动缩放和翻页按钮坐标转换。</p>
 */
@SideOnly(Side.CLIENT)
public class TiCGuiManual extends GuiManual {

    private static final int ANIMATIONDURATIONINMILLIS = 600;
    private static final float MAX_SCREEN_RATIO = 0.8f;
    private static final float MIN_SCALE = 1.0f;
    private static final float NEXT_BUTTON_X_RATIO = 0.8f;
    private static final float PREVIOUS_BUTTON_X_RATIO = 0.9f;
    private static final float BUTTON_Y_RATIO = 0.85f;
    private static final int LEFT_PAGE_CONTENT_X = 16;
    private static final int RIGHT_PAGE_CONTENT_X = 220;
    private static final int PAGE_CONTENT_Y = 12;
    private static final double FLYIN_DURATION = 0.55;
    private static final double OVERSHOOT_DURATION = 0.1;
    private static final double EXTENT = 0.02;

    Document manual;
    int bookImageWidth = 206;
    int bookImageHeight = 200;
    int currentPage;
    int maxPages;
    BookData bData;

    private boolean needUpdateAnimation;

    private TurnPageButton buttonNextPage;
    private TurnPageButton buttonPreviousPage;
    private static ResourceLocation bookRight;// = new ResourceLocation("mantle", "textures/gui/bookright.png");
    private static ResourceLocation bookLeft;// = new ResourceLocation("mantle", "textures/gui/bookleft.png");

    private long guiOpenTime;

    private int baseDrawingX;
    private int baseDrawingY;
    private float currentScale = MIN_SCALE;
    BookPage pageLeft;
    BookPage pageRight;

    /**
     * 创建 TConstruct 手册界面。
     *
     * @param stack 手册物品栈
     * @param data Mantle 书籍数据
     */
    public TiCGuiManual(ItemStack stack, BookData data) {
        super(stack, data);
        this.mc = Minecraft.getMinecraft();
        currentPage = 0;
        manual = data.getDoc();
        if (data.font != null) this.fonts = data.font;
        bookLeft = data.leftImage;
        bookRight = data.rightImage;
        this.bData = data;
        this.guiOpenTime = System.currentTimeMillis();
        this.needUpdateAnimation = true;

    }

    /**
     * 初始化手册页面和翻页按钮。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        maxPages = manual.getElementsByTagName("page").getLength();
        ticUpdateText();
        this.buttonList.add(this.buttonNextPage = new TurnPageButton(1, 0, 0, true, bData));
        this.buttonList.add(this.buttonPreviousPage = new TurnPageButton(2, 0, 0, false, bData));
        updateButtonVisibility();
    }

    /**
     * 根据当前页码更新翻页按钮可见性。
     */
    private void updateButtonVisibility() {
        buttonPreviousPage.visible = currentPage > 0;
        buttonNextPage.visible = currentPage < maxPages - 2;
    }

    /**
     * 响应翻页按钮点击。
     *
     * @param button 被点击的按钮
     */
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled) {
            changePage(button.id);
            updateButtonVisibility();
            ticUpdateText();
        }
    }

    /**
     * 读取当前左右页对应的页面数据。
     */
    void ticUpdateText() {
        if (maxPages % 2 == 1) {
            if (currentPage > maxPages) currentPage = maxPages;
        } else {
            if (currentPage >= maxPages) currentPage = maxPages - 2;
        }
        if (currentPage % 2 == 1) currentPage--;
        if (currentPage < 0) currentPage = 0;

        NodeList nList = manual.getElementsByTagName("page");

        Node node = nList.item(currentPage);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            Class<? extends BookPage> clazz = MProxyClient.getPageClass(element.getAttribute("type"));
            if (clazz != null) {
                try {
                    pageLeft = clazz.getDeclaredConstructor().newInstance();
                    pageLeft.init(this, 0);
                    pageLeft.readPageFromXML(element);
                } catch (Exception e) {
                    TConstruct.logger.error(e);
                }
            } else {
                pageLeft = null;
            }
        }

        node = nList.item(currentPage + 1);
        if (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            Class<? extends BookPage> clazz = MProxyClient.getPageClass(element.getAttribute("type"));
            if (clazz != null) {
                try {
                    pageRight = clazz.getDeclaredConstructor().newInstance();
                    pageRight.init(this, 1);
                    pageRight.readPageFromXML(element);
                } catch (Exception e) {
                    TConstruct.logger.error(e);
                }
            } else {
                pageRight = null;
            }
        } else {
            pageRight = null;
        }
    }

    /**
     * 根据按钮 ID 切换当前页码。
     *
     * @param buttonId 翻页按钮 ID
     */
    private void changePage(int buttonId) {
        if (buttonId == 1) {
            currentPage += 2;
        }
        if (buttonId == 2) {
            currentPage -= 2;
        }
    }

    /**
     * 绘制手册界面。
     *
     * @param par1 屏幕坐标系下的鼠标 X 坐标
     * @param par2 屏幕坐标系下的鼠标 Y 坐标
     * @param par3 局部渲染时间
     */
    @Override
    public void drawScreen(int par1, int par2, float par3) {
        this.currentScale = this.getBookScale();
        this.updateBookPosition();

        int rightPageX = this.toScaledGuiCoordinate(this.baseDrawingX);
        int drawY = this.toScaledGuiCoordinate(this.baseDrawingY);
        int leftPageX = rightPageX - this.bookImageWidth;

        GL11.glPushMatrix();
        try {
            GL11.glScalef(this.currentScale, this.currentScale, 1.0f);
            this.drawBookBackground(leftPageX, rightPageX, drawY);
            if (!this.needUpdateAnimation) this.drawButtons(par1, par2, rightPageX, drawY);
            this.drawPages(leftPageX, drawY);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * 处理鼠标点击。
     *
     * <p>按钮绘制在缩放后的矩阵中，点击判断前必须把鼠标屏幕坐标转换回未缩放 GUI 坐标。</p>
     *
     * @param mouseX 屏幕坐标系下的鼠标 X 坐标
     * @param mouseY 屏幕坐标系下的鼠标 Y 坐标
     * @param mouseButton 鼠标按钮 ID
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.needUpdateAnimation) return;

        this.currentScale = this.getBookScale();
        this.updateBookPosition();

        int rightPageX = this.toScaledGuiCoordinate(this.baseDrawingX);
        int drawY = this.toScaledGuiCoordinate(this.baseDrawingY);
        this.updateButtonPositions(rightPageX, drawY);

        super.mouseClicked(this.toUnscaledMouseCoordinate(mouseX), this.toUnscaledMouseCoordinate(mouseY), mouseButton);
    }

    /**
     * 绘制翻页按钮和标签。
     *
     * @param mouseX 屏幕坐标系下的鼠标 X 坐标
     * @param mouseY 屏幕坐标系下的鼠标 Y 坐标
     * @param x 右页未缩放 X 坐标
     * @param y 书本未缩放 Y 坐标
     */
    public void drawButtons(int mouseX, int mouseY, int x, int y) {
        this.updateButtonPositions(x, y);

        int unscaledMouseX = this.toUnscaledMouseCoordinate(mouseX);
        int unscaledMouseY = this.toUnscaledMouseCoordinate(mouseY);

        this.buttonNextPage.drawButton(this.mc, unscaledMouseX, unscaledMouseY);
        this.buttonPreviousPage.drawButton(this.mc, unscaledMouseX, unscaledMouseY);

        int k;
        for (k = 0; k < this.labelList.size(); ++k) {
            ((GuiLabel) this.labelList.get(k)).func_146159_a(this.mc, unscaledMouseX, unscaledMouseY);
        }
    }

    /**
     * 计算当前窗口下的手册缩放比例。
     *
     * @return 缩放比例，最小值为 1
     */
    private float getBookScale() {
        return Math.max(
                MIN_SCALE,
                Math.min(
                        this.width * MAX_SCREEN_RATIO / (this.bookImageWidth * 2.0f),
                        this.height * MAX_SCREEN_RATIO / this.bookImageHeight));
    }

    /**
     * 更新书本在屏幕坐标系中的目标位置。
     */
    private void updateBookPosition() {
        if (this.needUpdateAnimation) {
            float progress = (System.currentTimeMillis() - this.guiOpenTime) * 1.0f / ANIMATIONDURATIONINMILLIS;
            int[] point = this.getOvershootPosition(progress);
            this.baseDrawingX = point[0];
            this.baseDrawingY = point[1];
            if (progress >= 1.0f) this.needUpdateAnimation = false;
        } else {
            this.baseDrawingX = this.getTargetRightPageX();
            this.baseDrawingY = this.getTargetBookY();
        }
    }

    /**
     * 绘制左右书页背景。
     *
     * @param leftPageX 左页未缩放 X 坐标
     * @param rightPageX 右页未缩放 X 坐标
     * @param drawY 未缩放 Y 坐标
     */
    private void drawBookBackground(int leftPageX, int rightPageX, int drawY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(bookRight);
        this.drawTexturedModalRect(rightPageX, drawY, 0, 0, this.bookImageWidth, this.bookImageHeight);

        this.mc.getTextureManager().bindTexture(bookLeft);
        this.drawTexturedModalRect(
                leftPageX,
                drawY,
                256 - this.bookImageWidth,
                0,
                this.bookImageWidth,
                this.bookImageHeight);
    }

    /**
     * 绘制左右页内容。
     *
     * @param leftPageX 左页未缩放 X 坐标
     * @param drawY 未缩放 Y 坐标
     */
    private void drawPages(int leftPageX, int drawY) {
        if (pageLeft != null) pageLeft.renderBackgroundLayer(leftPageX + LEFT_PAGE_CONTENT_X, drawY + PAGE_CONTENT_Y);
        if (pageRight != null) pageRight.renderBackgroundLayer(leftPageX + RIGHT_PAGE_CONTENT_X, drawY + PAGE_CONTENT_Y);
        if (pageLeft != null)
            pageLeft.renderContentLayer(leftPageX + LEFT_PAGE_CONTENT_X, drawY + PAGE_CONTENT_Y, bData.isTranslatable);
        if (pageRight != null)
            pageRight.renderContentLayer(
                    leftPageX + RIGHT_PAGE_CONTENT_X,
                    drawY + PAGE_CONTENT_Y,
                    bData.isTranslatable);
    }

    /**
     * 更新翻页按钮在未缩放 GUI 坐标系中的位置。
     *
     * @param rightPageX 右页未缩放 X 坐标
     * @param drawY 书本未缩放 Y 坐标
     */
    private void updateButtonPositions(int rightPageX, int drawY) {
        this.buttonNextPage.xPosition = (int) (rightPageX + this.bookImageWidth * NEXT_BUTTON_X_RATIO);
        this.buttonPreviousPage.xPosition = (int) (rightPageX - this.bookImageWidth * PREVIOUS_BUTTON_X_RATIO);

        this.buttonNextPage.yPosition = (int) (drawY + this.bookImageHeight * BUTTON_Y_RATIO);
        this.buttonPreviousPage.yPosition = (int) (drawY + this.bookImageHeight * BUTTON_Y_RATIO);
    }

    /**
     * 将屏幕坐标转换为未缩放 GUI 坐标。
     *
     * @param coordinate 屏幕坐标
     * @return 未缩放 GUI 坐标
     */
    private int toUnscaledMouseCoordinate(int coordinate) {
        return (int) (coordinate / this.currentScale);
    }

    /**
     * 将屏幕目标坐标转换为当前缩放矩阵内的绘制坐标。
     *
     * @param coordinate 屏幕目标坐标
     * @return 未缩放绘制坐标
     */
    private int toScaledGuiCoordinate(int coordinate) {
        return (int) (coordinate / this.currentScale);
    }

    /**
     * 获取右页最终停靠的屏幕 X 坐标。
     *
     * @return 右页屏幕 X 坐标
     */
    private int getTargetRightPageX() {
        return this.width / 2;
    }

    /**
     * 获取书本最终停靠的屏幕 Y 坐标。
     *
     * @return 书本屏幕 Y 坐标
     */
    private int getTargetBookY() {
        return (int) ((this.height - this.bookImageHeight * this.currentScale) / 2.0f);
    }

    /**
     * 获取 Minecraft 客户端实例。
     *
     * @return Minecraft 客户端实例
     */
    @Override
    public Minecraft getMC() {
        return mc;
    }

    /**
     * 手册界面不暂停游戏。
     *
     * @return 始终返回 false
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 根据打开动画进度计算书本屏幕坐标。
     *
     * @param progress 全局动画进度，0 为开始，1 为结束
     * @return 当前书本右页 X 坐标和顶部 Y 坐标
     */
    private int[] getOvershootPosition(float progress) {

        int endX = this.getTargetRightPageX();
        int startX = endX;

        int endY = this.getTargetBookY();
        int startY = (int) (this.height + this.bookImageHeight * this.currentScale);

        // 将动画进度限制在有效范围内。
        double t = Math.min(Math.max(progress, 0.0), 1.0);

        double factor;

        if (t <= FLYIN_DURATION) {
            // 第一阶段：从屏幕下方向目标位置线性飞入。
            double phaseT = t / FLYIN_DURATION;
            factor = phaseT;
        } else if (t <= FLYIN_DURATION + OVERSHOOT_DURATION) {
            // 第二阶段：超过目标位置少量距离，形成回弹感。
            double phaseT = (t - FLYIN_DURATION) / OVERSHOOT_DURATION;
            double eased = 1.0 - Math.pow(1.0 - phaseT, 2);
            factor = 1.0 + EXTENT * eased;
        } else {
            // 第三阶段：从超出位置回到最终目标位置。
            double remaining = 1.0 - (FLYIN_DURATION + OVERSHOOT_DURATION);
            double phaseT = (t - (FLYIN_DURATION + OVERSHOOT_DURATION)) / remaining;
            double peak = 1.0 + EXTENT;
            factor = peak + (1.0 - peak) * phaseT;
        }

        // 额外兜底，避免浮点误差导致插值超出预期范围。
        factor = Math.min(factor, 1.0 + EXTENT);

        int x = (int) (startX + (endX - startX) * factor);
        int y = (int) (startY + (endY - startY) * factor);

        return new int[] { x, y };
    }

}
