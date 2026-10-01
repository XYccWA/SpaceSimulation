package org.xyccwa.space_simulation.asteroid.data;

import org.xyccwa.space_simulation.asteroid.AsteroidUniverse;

import java.util.Collections;
import java.util.List;

/**
 * 数据包加载产物（不可变）：编译后的小行星宇宙 + 加载清单。
 *
 * 由 {@link AsteroidDataLoader} 在数据包重载时构建并原子安装到
 * {@link org.xyccwa.space_simulation.asteroid.AsteroidProximityService}；
 * 旧 catalog 被旧检索器继续使用，直到新宇宙一并接管（无半成品状态）。
 */
public final class AsteroidCatalog {

    /** 编译后的宇宙（含全部环带的档结构）。 */
    public final AsteroidUniverse universe;
    /** 解析/校验错误（不致命；致命错误导致 fallback=true）。 */
    public final List<String> errors;
    /** 成功解析的环带文件数。 */
    public final int beltFileCount;
    /** 成功解析的类型文件数。 */
    public final int typeFileCount;
    /** 是否回退到内置默认单带（数据包缺失或全部无效）。 */
    public final boolean fallback;

    public AsteroidCatalog(AsteroidUniverse universe, List<String> errors,
                           int beltFileCount, int typeFileCount, boolean fallback) {
        this.universe = universe;
        this.errors = Collections.unmodifiableList(errors);
        this.beltFileCount = beltFileCount;
        this.typeFileCount = typeFileCount;
        this.fallback = fallback;
    }
}
