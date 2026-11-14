package dev.oumuv.budlog.common;

import java.util.List;

public class RecordPage<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long total;

    public RecordPage(List<T> content, int page, int size, long total) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getContent() {
        return content;
    }

    public int getNumber() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return total;
    }

    public int getTotalPages() {
        return size == 0 ? 1 : (int) Math.ceil((double) total / size);
    }
}
