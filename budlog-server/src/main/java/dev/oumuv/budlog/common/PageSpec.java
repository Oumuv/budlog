package dev.oumuv.budlog.common;

public final class PageSpec {
    private final int page;
    private final int size;

    private PageSpec(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw BusinessException.validation("分页参数无效，size 须为 1 至 100，偏移不得溢出");
        }
        this.page = page;
        this.size = size;
    }

    public static PageSpec of(int page, int size) { return new PageSpec(page, size); }
    public int getPage() { return page; }
    public int getSize() { return size; }
}
