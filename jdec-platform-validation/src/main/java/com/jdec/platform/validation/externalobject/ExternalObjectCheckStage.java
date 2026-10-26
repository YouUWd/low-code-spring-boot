package com.jdec.platform.validation.externalobject;

/**
 * 外部对象校验层级。
 *
 * <p>校验按本枚举的声明顺序递进执行，每层内部把全部不通过项收集完再返回；前一层不通过时，后续层级不再执行。
 *
 * <ul>
 *   <li>{@link #REQUIRED}：字段是否有值。未填的字段不做格式校验与查重，避免“字段没填却提示重复”；
 *   <li>{@link #BUSINESS}：已填值的格式与取值是否符合业务规则；
 *   <li>{@link #DUPLICATE}：已填值的字段是否与其他已保存数据重复。
 * </ul>
 */
public enum ExternalObjectCheckStage {

    /** 必填校验 */
    REQUIRED("必填校验"),

    /** 业务规则校验 */
    BUSINESS("业务规则校验"),

    /** 查重校验 */
    DUPLICATE("查重校验");

    private final String title;

    ExternalObjectCheckStage(String title) {
        this.title = title;
    }

    /** 层级名称，可直接展示给前端。 */
    public String title() {
        return title;
    }
}
