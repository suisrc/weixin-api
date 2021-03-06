package com.suisrc.weixin.core.check;

/**
 * 类型assert工具
 * @author Y13
 *
 */
public class TypeEqualsAssert implements TypeAssert {
    
    /**
     * 执行assert
     * @param type
     * @return
     */
    public boolean apply(String type, String value) {
        return type != null && type.equals(value);
    }

}
