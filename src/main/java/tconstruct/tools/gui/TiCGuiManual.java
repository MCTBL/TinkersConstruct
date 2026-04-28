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
import mantle.client.pages.BookPage;
import tconstruct.TConstruct;

/**
 * TConstruct manual screen.
 *
 * <p>This class reuses Mantle book data, page types, and textures while handling the open animation, automatic scaling,
 * and page button hitboxes locally.</p>
 */
@SideOnly(Side.CLIENT)
public class TiCGuiManual extends GuiManual {

    private static final int ANIMATIONDURATIONINMILLIS = 600;
    private static final float MAX_SCREEN_RATIO = 0.8f;
    private static final float MIN_SCALE = 1.0f;
    private static final float NEXT_BUTTON_X_RATIO = 0.8f;
    private static final float PREVIOUS_BUTTON_X_RATIO = 0.9f;
    private static final float BUTTON_Y_RATIO = 0.85f;
    private static final int PAGE_BUTTON_WIDTH = 23;
    private static final int PAGE_BUTTON_HEIGHT = 13;
    private static final int PAGE_BUTTON_TEXTURE_Y = 192;
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

    private PageButton buttonNextPage;
    private PageButton buttonPreviousPage;
    private static ResourceLocation bookRight;// = new ResourceLocation("mantle", "textures/gui/bookright.png");
    private static ResourceLocation bookLeft;// = new ResourceLocation("mantle", "textures/gui/bookleft.png");

    private long guiOpenTime;

    private int baseDrawingX;
    private int baseDrawingY;
    private float currentScale = MIN_SCALE;
    BookPage pageLeft;
    BookPage pageRight;

    /**
     * Creates the TConstruct manual screen.
     *
     * @param stack manual item stack
     * @param data Mantle book data
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
     * Initializes manual pages and page buttons.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        maxPages = manual.getElementsByTagName("page").getLength();
        ticUpdateText();
        this.buttonList.add(this.buttonNextPage = new PageButton(1, true));
        this.buttonList.add(this.buttonPreviousPage = new PageButton(2, false));
        updateButtonVisibility();
    }

    /**
     * Updates page button visibility for the current page.
     */
    private void updateButtonVisibility() {
        buttonPreviousPage.visible = currentPage > 0;
        buttonNextPage.visible = currentPage < maxPages - 2;
    }

    /**
     * Handles page button clicks.
     *
     * @param button clicked button
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
     * Loads page data for the current left and right pages.
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
     * Changes the current page using the clicked button ID.
     *
     * @param buttonId page button ID
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
     * Draws the manual screen.
     *
     * @param par1 mouse X coordinate in screen space
     * @param par2 mouse Y coordinate in screen space
     * @param par3 partial render time
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
            this.drawPages(leftPageX, drawY);
        } finally {
            GL11.glPopMatrix();
        }

        this.updateButtonPositions();
        this.drawButtons(par1, par2);
    }

    /**
     * Handles mouse clicks.
     *
     * <p>Buttons are managed in screen space, so vanilla button click checks can use the original mouse coordinates.</p>
     *
     * @param mouseX mouse X coordinate in screen space
     * @param mouseY mouse Y coordinate in screen space
     * @param mouseButton mouse button ID
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.currentScale = this.getBookScale();
        this.updateBookPosition();
        this.updateButtonPositions();

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Draws page buttons and labels.
     *
     * @param mouseX mouse X coordinate in screen space
     * @param mouseY mouse Y coordinate in screen space
     */
    public void drawButtons(int mouseX, int mouseY) {
        this.buttonNextPage.drawButton(this.mc, mouseX, mouseY);
        this.buttonPreviousPage.drawButton(this.mc, mouseX, mouseY);

        int k;
        for (k = 0; k < this.labelList.size(); ++k) {
            ((GuiLabel) this.labelList.get(k)).func_146159_a(this.mc, mouseX, mouseY);
        }
    }

    /**
     * Calculates the manual scale for the current window size.
     *
     * @return scale factor, with a minimum of 1
     */
    private float getBookScale() {
        return Math.max(
                MIN_SCALE,
                Math.min(
                        this.width * MAX_SCREEN_RATIO / (this.bookImageWidth * 2.0f),
                        this.height * MAX_SCREEN_RATIO / this.bookImageHeight));
    }

    /**
     * Updates the book target position in screen space.
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
     * Draws the left and right page backgrounds.
     *
     * @param leftPageX unscaled left page X coordinate
     * @param rightPageX unscaled right page X coordinate
     * @param drawY unscaled Y coordinate
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
     * Draws left and right page contents.
     *
     * @param leftPageX unscaled left page X coordinate
     * @param drawY unscaled Y coordinate
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
     * Updates page button positions and hitboxes in screen-space coordinates.
     */
    private void updateButtonPositions() {
        this.buttonNextPage.setScale(this.currentScale);
        this.buttonPreviousPage.setScale(this.currentScale);

        this.buttonNextPage.xPosition = Math.round(
                this.baseDrawingX + this.bookImageWidth * this.currentScale * NEXT_BUTTON_X_RATIO);
        this.buttonPreviousPage.xPosition = Math.round(
                this.baseDrawingX - this.bookImageWidth * this.currentScale * PREVIOUS_BUTTON_X_RATIO);

        int buttonY = Math.round(this.baseDrawingY + this.bookImageHeight * this.currentScale * BUTTON_Y_RATIO);
        this.buttonNextPage.yPosition = buttonY;
        this.buttonPreviousPage.yPosition = buttonY;
    }

    /**
     * Converts a screen-space target coordinate to a draw coordinate inside the current scaled matrix.
     *
     * @param coordinate screen-space target coordinate
     * @return unscaled draw coordinate
     */
    private int toScaledGuiCoordinate(int coordinate) {
        return (int) (coordinate / this.currentScale);
    }

    /**
     * Gets the final screen-space X coordinate for the right page.
     *
     * @return right page screen-space X coordinate
     */
    private int getTargetRightPageX() {
        return this.width / 2;
    }

    /**
     * Gets the final screen-space Y coordinate for the book.
     *
     * @return book screen-space Y coordinate
     */
    private int getTargetBookY() {
        return (int) ((this.height - this.bookImageHeight * this.currentScale) / 2.0f);
    }

    /**
     * Gets the Minecraft client instance.
     *
     * @return Minecraft client instance
     */
    @Override
    public Minecraft getMC() {
        return mc;
    }

    /**
     * Keeps the manual screen from pausing the game.
     *
     * @return always false
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Calculates the book screen-space position for the open animation progress.
     *
     * @param progress global animation progress, where 0 is the start and 1 is the end
     * @return current right page X coordinate and top Y coordinate
     */
    private int[] getOvershootPosition(float progress) {

        int endX = this.getTargetRightPageX();
        int startX = endX;

        int endY = this.getTargetBookY();
        int startY = (int) (this.height + this.bookImageHeight * this.currentScale);

        // Clamp the animation progress to the valid range.
        double t = Math.min(Math.max(progress, 0.0), 1.0);

        double factor;

        if (t <= FLYIN_DURATION) {
            // Phase 1: fly in linearly from below the screen.
            double phaseT = t / FLYIN_DURATION;
            factor = phaseT;
        } else if (t <= FLYIN_DURATION + OVERSHOOT_DURATION) {
            // Phase 2: move slightly past the target to create a bounce.
            double phaseT = (t - FLYIN_DURATION) / OVERSHOOT_DURATION;
            double eased = 1.0 - Math.pow(1.0 - phaseT, 2);
            factor = 1.0 + EXTENT * eased;
        } else {
            // Phase 3: settle back from the overshoot to the final target.
            double remaining = 1.0 - (FLYIN_DURATION + OVERSHOOT_DURATION);
            double phaseT = (t - (FLYIN_DURATION + OVERSHOOT_DURATION)) / remaining;
            double peak = 1.0 + EXTENT;
            factor = peak + (1.0 - peak) * phaseT;
        }

        // Guard against floating point drift moving interpolation outside the intended range.
        factor = Math.min(factor, 1.0 + EXTENT);

        int x = (int) (startX + (endX - startX) * factor);
        int y = (int) (startY + (endY - startY) * factor);

        return new int[] { x, y };
    }

    /**
     * Page turn button that is drawn and hit-tested directly in screen space.
     */
    private class PageButton extends GuiButton {

        private final boolean nextPage;
        private float buttonScale = MIN_SCALE;

        /**
         * Creates a page turn button.
         *
         * @param id button ID
         * @param nextPage true for the next-page button, false for the previous-page button
         */
        PageButton(int id, boolean nextPage) {
            super(id, 0, 0, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT, "");
            this.nextPage = nextPage;
        }

        /**
         * Updates the visual scale and screen-space hitbox size.
         *
         * @param scale current manual scale
         */
        void setScale(float scale) {
            this.buttonScale = scale;
            this.width = Math.round(PAGE_BUTTON_WIDTH * scale);
            this.height = Math.round(PAGE_BUTTON_HEIGHT * scale);
        }

        /**
         * Draws the button in screen space so its hitbox matches the rendered position.
         *
         * @param minecraft Minecraft client instance
         * @param mouseX mouse X coordinate in screen space
         * @param mouseY mouse Y coordinate in screen space
         */
        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!this.visible) return;

            boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;

            int textureX = hovered ? PAGE_BUTTON_WIDTH : 0;
            int textureY = PAGE_BUTTON_TEXTURE_Y;
            if (!this.nextPage) textureY += PAGE_BUTTON_HEIGHT;

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.getTextureManager().bindTexture(bookLeft);

            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(this.xPosition, this.yPosition, 0.0F);
                GL11.glScalef(this.buttonScale, this.buttonScale, 1.0F);
                TiCGuiManual.this.drawTexturedModalRect(
                        0,
                        0,
                        textureX,
                        textureY,
                        PAGE_BUTTON_WIDTH,
                        PAGE_BUTTON_HEIGHT);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

}
