package com.jdec.platform.shared.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源切换注解。
 *
 * <p>可标注在 <b>类</b> 或 <b>方法</b> 上，方法级注解优先于类级注解。 未标注时默认使用主数据源（{@code primary}）。
 *
 * <p><b>注意：</b>本注解依赖 Spring AOP 代理拦截，因此 <b>只能用于 Spring Bean 的具体类</b>（如
 * {@code @Service}、{@code @Component}）。 <b>不能</b>标注在 MyBatis Mapper 接口上，因为 Mapper 使用 JDK 动态代理，AOP
 * 切面无法正确拦截接口上的注解。 如需为 Mapper 方法切换数据源，请在调用该 Mapper 的 Service 方法上标注此注解。
 *
 * <p>支持嵌套调用：内层方法使用不同数据源时，外层数据源会被栈保存，内层执行完毕后自动恢复。
 *
 * <pre>
 * // 示例：将整个 Service 切换到 coal-industry 数据源
 * {@literal @}DataSource("coal-industry")
 * {@literal @}Service
 * public class CoalService { ... }
 *
 * // 示例：仅将某个方法切换到 ipd 数据源
 * {@literal @}DataSource("ipd")
 * public List{@literal <Order>} getIpdOrders() { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataSource {

    /** 数据源名称，对应 YAML 配置中 {@code spring.datasource.dynamic.datasources} 下的 key。 */
    String value() default DataSourceConstants.PRIMARY;
}
