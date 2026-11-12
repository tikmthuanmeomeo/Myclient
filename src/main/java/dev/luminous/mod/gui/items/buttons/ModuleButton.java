package dev.luminous.mod.gui.items.buttons;

import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.gui.WeatherTheme;
import dev.luminous.mod.gui.items.Item;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.*;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleButton
        extends Button {
    private final Module module;
    private List<Item> items = new ArrayList<>();
    public boolean subOpen;
    public double itemHeight;
    public final Animation animation = new Animation();

    public ModuleButton(Module module) {
        super(module.getName());
        this.module = module;
        this.initSettings();
    }

    private static class Section {
        final Enum<?> page;
        final SectionButton header;
        final List<Item> items = new ArrayList<>();

        Section(Enum<?> page, boolean open) {
            this.page = page;
            this.header = new SectionButton(page.name(), open);
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private final List<Item> commonItems = new ArrayList<>();
    private EnumSetting<?> pageSetting;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPage(EnumSetting<?> setting, Enum<?> value) {
        ((EnumSetting) setting).setValue(value);
    }

    private static Item makeItem(Setting setting) {
        if (setting instanceof BooleanSetting s) return new BooleanButton(s);
        if (setting instanceof BindSetting s) return new BindButton(s);
        if (setting instanceof StringSetting s) return new StringButton(s);
        if (setting instanceof SliderSetting s) return new SliderButton(s);
        if (setting instanceof EnumSetting<?> s) return new EnumButton(s);
        if (setting instanceof ColorSetting s) return new PickerButton(s);
        return null;
    }

    private boolean isKey(Setting setting) {
        return setting == this.module.getBindSetting();
    }

    public void initSettings() {
        this.sections.clear();
        this.commonItems.clear();
        this.pageSetting = null;
        for (Setting setting : this.module.getSettings()) {
            if (setting instanceof EnumSetting<?> e && e.getName().equals("Page")) {
                this.pageSetting = e;
            }
        }
        Item keyItem = null;
        if (this.pageSetting == null) {
            for (Setting setting : this.module.getSettings()) {
                if (setting == this.module.drawn) continue;
                Item item = makeItem(setting);
                if (item == null) continue;
                if (isKey(setting)) {
                    keyItem = item;
                } else {
                    this.commonItems.add(item);
                }
            }
        } else {
            Enum<?>[] pages = this.pageSetting.getValue().getDeclaringClass().getEnumConstants();
            Enum<?> original = this.pageSetting.getValue();
            Section last = null;
            try {
                for (Setting setting : this.module.getSettings()) {
                    if (setting == this.pageSetting || setting == this.module.drawn) continue;
                    Item item = makeItem(setting);
                    if (item == null) continue;
                    if (isKey(setting)) {
                        keyItem = item;
                        continue;
                    }
                    int visibleCount = 0;
                    Enum<?> first = null;
                    for (Enum<?> p : pages) {
                        setPage(this.pageSetting, p);
                        if (setting.isVisible()) {
                            visibleCount++;
                            if (first == null) first = p;
                        }
                    }
                    if (visibleCount == pages.length) {
                        this.commonItems.add(item);
                        continue;
                    }
                    Section target = null;
                    if (first != null) {
                        for (Section sec : this.sections) {
                            if (sec.page == first) target = sec;
                        }
                        if (target == null) {
                            target = new Section(first, false);
                            this.sections.add(target);
                        }
                    } else {
                        target = last;
                    }
                    if (target == null) {
                        this.commonItems.add(item);
                    } else {
                        target.items.add(item);
                        last = target;
                    }
                }
            } finally {
                setPage(this.pageSetting, original);
            }
            this.sections.sort((a, b) -> Integer.compare(a.page.ordinal(), b.page.ordinal()));
            if (!this.sections.isEmpty()) this.sections.get(0).header.open = true;
        }
        if (keyItem != null) this.commonItems.add(keyItem);

        ArrayList<Item> newItems = new ArrayList<>();
        for (Section sec : this.sections) {
            newItems.add(sec.header);
            newItems.addAll(sec.items);
        }
        newItems.addAll(this.commonItems);
        this.items = newItems;
    }

    @Override
    public void update() {
        if (this.pageSetting == null) {
            for (Item item : this.items) {
                item.update();
            }
            return;
        }
        Enum<?> original = this.pageSetting.getValue();
        try {
            for (Section sec : this.sections) {
                setPage(this.pageSetting, sec.page);
                for (Item item : sec.items) {
                    item.update();
                    if (!sec.header.open) item.setHidden(true);
                }
            }
        } finally {
            setPage(this.pageSetting, original);
        }
        for (Item item : this.commonItems) {
            item.update();
        }
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        Color color = ClickGui.getInstance().color.getValue();
        int rgba = this.getState() ? (!this.isHovering(mouseX, mouseY) ? WeatherTheme.ROW_ON_LEFT : WeatherTheme.ROW_ON_LEFT_HOVER) : (!this.isHovering(mouseX, mouseY) ? defaultColor : hoverColor);
        if (this.getState()) {
            Render2DUtil.horizontalGradient(context.getMatrices(), this.x, this.y, this.x + (float) this.width, this.y + (float) this.height - 0.5f,
                    this.isHovering(mouseX, mouseY) ? WeatherTheme.ROW_ON_LEFT_HOVER : WeatherTheme.ROW_ON_LEFT, WeatherTheme.ROW_ON_RIGHT);
            Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + 2.5f, this.y + (float) this.height - 0.5f, WeatherTheme.ACCENT);
        } else {
            Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + (float) this.width, this.y + (float) this.height - 0.5f, rgba);
        }
        drawString(module.getDisplayName(), this.x + 6f, this.y - 2.0f - (float) ClickGuiScreen.getInstance().getTextOffset(), this.getState() ? enableTextColor : defaultTextColor);
        if (ClickGui.getInstance().gear.booleanValue) {
            drawString("...", this.x + width - 13f, this.y - 1.7f - (float) ClickGuiScreen.getInstance().getTextOffset(), ClickGui.getInstance().gear.getValue().getRGB());
        }
        if (this.subOpen || itemHeight > 0) {
            if (ClickGui.getInstance().line.getValue()) {
                double itemHeight = getItemHeight();
                Render2DUtil.drawLine(context.getMatrices(), x + 0.2f, (float) ((this.y + this.height) + itemHeight - .5f), this.x + 0.2f, this.y + height - .5f, rgba);

                Render2DUtil.drawLine(context.getMatrices(), x + this.width, (float) ((this.y + this.height) + itemHeight - .5f), this.x + this.width, this.y + height - .5f, rgba);

                Render2DUtil.drawLine(context.getMatrices(), x + 0.3f, (float) ((this.y + this.height) + itemHeight - .5f), this.x + this.width - 0.2f, (float) ((this.y + this.height) + itemHeight - .7f), rgba);
            }
            float height = this.height + 1;
            for (Item item : this.items) {
                if (!item.isHidden()) {
                    item.setHeight(this.height);
                    item.setLocation(this.x + 1.0f, this.y + (height));
                    item.setWidth(this.width - 9);
                    item.drawScreen(context, mouseX, mouseY, partialTicks);
                    height += item.getHeight() + 1;
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (!this.items.isEmpty()) {
            if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
                this.subOpen = !this.subOpen;
                sound();
            }
            if (this.subOpen) {
                for (Item item : this.items) {
                    if (item.isHidden()) continue;
                    item.mouseClicked(mouseX, mouseY, mouseButton);
                }
            }
        }
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        super.onKeyTyped(typedChar, keyCode);
        if (!this.items.isEmpty() && this.subOpen) {
            for (Item item : this.items) {
                if (item.isHidden()) continue;
                item.onKeyTyped(typedChar, keyCode);
            }
        }
    }

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (!this.items.isEmpty() && this.subOpen) {
            for (Item item : this.items) {
                if (item.isHidden()) continue;
                item.onKeyPressed(key);
            }
        }
    }

    public int getButtonHeight() {
        return super.getHeight();
    }

    public int getItemHeight() {
        int height = 2;
        for (Item item : this.items) {
            if (item.isHidden()) continue;
            height += item.getHeight() + 1;
        }
        return height;
    }

    @Override
    public int getHeight() {
        if (this.subOpen) {
            int height = super.getHeight();
            for (Item item : this.items) {
                if (item.isHidden()) continue;
                height += item.getHeight() + 1;
            }
            return height + 2;
        }
        return super.getHeight();
    }

    public Module getModule() {
        return this.module;
    }

    @Override
    public void toggle() {
        this.module.toggle();
    }

    @Override
    public boolean getState() {
        return this.module.isOn();
    }
}