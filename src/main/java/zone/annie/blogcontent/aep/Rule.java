package zone.annie.blogcontent.aep;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rule {
    /**
     * 方向, big: 大端 0 字节在左, little: 小端 0 字节在右
     */
    private String direction;
    private List<RuleDetail> details;

    public void sortByte() {
        if (CollectionUtils.isEmpty(details)) {
            return;
        }
        Collections.sort(details);
    }

    /**
     * 用于确认是否有重复的变量名, 如果有重复的变量名则返回重复的名字, 业务端应该处理
     * @return 重复的变量名
     */
    public Set<String> findDuplicateNames() {
        List<String> nameList = new ArrayList<>();
        Set<String> duplicates = new HashSet<>();
        if (CollectionUtils.isEmpty(this.details)) {
            return duplicates;
        }
        for (RuleDetail detail : details) {
            if (RuleType.BYTE.equals(detail.type)) {
                nameList.add(detail.byteValue.name);
            } else if (RuleType.BIT.equals(detail.type)) {
                for (RuleBitValueEnum v : detail.bitValue.ruleBitValueEnumList) {
                    nameList.add(v.name);
                }
            }
        }
        Set<String> seen = new HashSet<>();

        for (String item : nameList) {
            if (!seen.add(item)) {
                duplicates.add(item);
            }
        }
        return duplicates;
    }

    @Data
    public static class RuleDetail implements Comparable<RuleDetail> {
        /**
         * 用于通过 byte 排序时使用, type 为 byte 时, 该值为 byteIndexFrom, type 为 bit 时, 该值为 byteIndex 的值
         */
        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        @JsonIgnore
        private Integer byteIndex;

        /**
         * <p>数据类型</p>
         * <p>当数据为某些字节整体时, 类型为 byte</p>
         * <p>当数据为某些字节中精确到各 bit 时, 类型为 bit</p>
         */
        private String type;

        private RuleByteValue byteValue;
        private RuleBitValue bitValue;

        /**
         * 用于处理 byte 的值为枚举的情况, 如果 byte 不为枚举, 则应该直接取 byte 的字面量
         */
        public Map<Integer, String> byteValueToMap() {
            if (byteValue.ruleByteValueEnumList == null) {
                return new HashMap<>();
            }
            return byteValue.ruleByteValueEnumList.stream().collect(Collectors.toMap(RuleByteValueEnum::getByteValue, RuleByteValueEnum::getValue, (o, n) -> n));
        }

        /**
         * 用于处理 bit 的数据, 比如取 bit 所在 bit 位和值的关系
         */
        public Map<Integer, RuleBitValueEnum> bitValueToMap() {
            if (bitValue.ruleBitValueEnumList == null) {
                return new HashMap<>();
            }
            return bitValue.ruleBitValueEnumList.stream().collect(Collectors.toMap(RuleBitValueEnum::getBit, Function.identity(), (o, n) -> n));
        }

        /**
         * 用于将所有的值通过 byte 排序
         * @param o the object to be compared.
         */
        @Override
        public int compareTo(@Nonnull RuleDetail o) {
            if (byteIndex == null) {
                if (RuleType.BYTE.equals(type)) {
                    byteIndex = byteValue.byteIndexFrom;
                } else if (RuleType.BIT.equals(type)) {
                    byteIndex = bitValue.byteIndex;
                } else {
                    byteIndex = 0;
                }
            }
            if (o.byteIndex == null) {
                if (RuleType.BYTE.equals(o.type)) {
                    o.byteIndex = o.byteValue.byteIndexFrom;
                } else if (RuleType.BIT.equals(o.type)) {
                    o.byteIndex = o.bitValue.byteIndex;
                } else {
                    o.byteIndex = 0;
                }
            }
            return Integer.compare(this.byteIndex, o.byteIndex);
        }
    }

    @Data
    public static class RuleByteValue implements Comparable<RuleByteValue> {
        private String name;
        private Integer byteIndexFrom;
        private Integer byteIndexTo;
        /**
         * 仅当数据类型为 byte 时生效
         */
        private List<RuleByteValueEnum> ruleByteValueEnumList;

        @Override
        public int compareTo(RuleByteValue detail) {
            return Integer.compare(this.byteIndexFrom, detail.byteIndexFrom);
        }
    }

    @Data
    public static class RuleBitValue {
        private Integer byteIndex;
        private List<RuleBitValueEnum> ruleBitValueEnumList;
    }

    @Data
    public static class RuleByteValueEnum {
        /**
         * 字节对应的值, 如 ca, ff, 12 等
         */
        private Integer byteValue;
        /**
         * 字节对应的业务值, 如 0x12: 正常
         */
        private String value;
    }

    @Data
    public static class RuleBitValueEnum {
        private String name;
        /**
         * bit 位, 从 0 开始
         */
        private Integer bit;
        /**
         * 该 bit 位为 1 时的业务数据
         */
        private String oneValue;
        /**
         * 该 bit 位为 0 时的业务数据
         */
        private String zeroValue;
    }

    public static class RuleDirection {
        public static final String BIG = "big";
        public static final String LITTLE = "little";
    }

    public static class RuleType {
        public static final String BYTE = "byte";
        public static final String BIT = "bit";
    }

}
