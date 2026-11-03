package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.Info;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 外部对象主档表（顶部公共头核心信息） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface InfoMapper extends BaseMapper<Info> {

    /**
     * 根据一组父级业务编号批量查询潜在的子单据候选记录
     *
     * @param parentBizNos 父级业务编号集合
     * @return 匹配的子单据列表
     */
    @Select(
            "<script>"
                    + "SELECT * FROM info WHERE deleted = 0 AND parent_id IS NOT NULL "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(parent_id, '$.value')) IN "
                    + "<foreach item='item' collection='parentBizNos' open='(' separator=',' close=')'>"
                    + "#{item}"
                    + "</foreach>"
                    + "</script>")
    List<Info> selectCandidatesByParentBizNos(
            @Param("parentBizNos") Collection<String> parentBizNos);
}
