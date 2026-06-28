package rmn.insights.dto;

public enum MetricType {
    CLICKS("clicks"),
    IMPRESSIONS("impressions"),
    CLICK_TO_BASKET("click_to_basket");

    private final String columnName;

    MetricType(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }
}
