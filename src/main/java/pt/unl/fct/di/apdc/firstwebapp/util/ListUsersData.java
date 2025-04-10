package pt.unl.fct.di.apdc.firstwebapp.util;

public class ListUsersData {
    private Integer offset;
    private Integer limit;

    public ListUsersData() {
    }

    public Integer getOffset() {
        return offset != null ? offset : 0;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit != null ? limit : 100;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
