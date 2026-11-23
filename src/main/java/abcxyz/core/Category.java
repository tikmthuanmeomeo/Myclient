package abcxyz.core;

public enum Category {
    Combat("Combat"), Movement("Movement"), Render("Render"), BaseFinder("Base Finder"), Misc("Misc");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
