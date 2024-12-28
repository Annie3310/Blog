# 通用的 AEP 解析引擎

[AEP](https://www.ctwing.cn) 可以理解为中国电信的 [NB-IoT](https://baike.baidu.com/item/NB-IoT/19420464) 网关,
不同的设备发送的数据格式都是不同的, 且大多数都是二进制数据, 如果批量管理设备时每次新增购设备都重新开发的话代价就会有点大,
为此开发了一个通用的二进制数据解析引擎, 可以将二进制数据通过自定义的 DSL 翻译成 JSON

## 物联网设备发送的数据示例

| byte | 19 |  03   |    01     |    f4     |
|:----:|:--:|:-----:|:---------:|:---------:|
| 业务意义 | 温度 | 取 bit | 功率 byte 1 | 功率 byte 1 |

**byte 2** 的二进制展开

| bit  | 0  |   0   |     0     |     0     | 0 |  1   |  1   | 0 |
|:----:|:--:|:-----:|:---------:|:---------:|:-:|:----:|:----:|:-:|
| 业务意义 | 0  |   0   |     0     |     0     | 0 | 是否过热 | 是否过干 | 0 |

为了解析诸如此类数据做出如下实现

## 首先定义一下 DSL 的结构
```json
{
  "direction": "big/little",
  "details": [
    {
      "type": "byte/bit",
      "byteValue": {
        "name": "变量名",
        "byteIndexFrom": 0,
        "byteIndexTo": 1,
        "ruleByteValueEnumList": [
          {
            "byteValue": "ca/ff/12 等, 为二进制数据的十六进制表示",
            "value": "实际对应的业务值, 如 ff 对应 黑, 12 对应不正常等, 此处为枚举的定义"
          }
        ]
      },
      "bitValue": {
        "byteIndex": 0,
        "ruleBitValueEnumList": [
          {
            "name": "变量名",
            "bit": 0,
            "oneValue": "该 bit 为 1 时该变量的值, 如某开关的状态",
            "zeroValue": "该 bit 为 0 时该变量的值"
          }
        ]
      }
    }
  ]
}
```

### 对字段的解释

其中 details 列表的每一项应该为一单元字节变量

如 0 到 1 字节共 16 bit 数据为同一项, 如某机器的功率, 最低为 500, 此时 1 字节是无法表示该址

如某字节的多个 bit 在同一 byte 上, 则该 byte 上的 bit 变量都定义在一条 details 中

字段的解释

- direction: 为 big 或 little, 用于区分数据是大端数据还是小端数据
- details[]: 变量单元列表
    - type: 标记该单元为 byte 值还是 bit 值
    - byteValue: 只有 details[].type 为 byte 时, 该值有效
        - name: 该单元的变量名
        - byteIndexFrom: 某单元变量需要由多个 byte 组成时用来确认变量的起始位置
        - byteIndexTo: 某单元变量需要由多个 byte 组成时用来确认变量的结束位置, 如果只需要 1 byte, 则该值和 - details[]
          .byteValue.byteIndexFrom 相同
        - ruleByteValueEnumList[]: 如果 byte 表示的值就为字面量本身, 则该值无意义, 如果需要为 byte 的值设定枚举, 则定义在此处
            - byteValue: ca/ff/12 等, 为二进制数据的十六进制表示
            - value: 实际对应的业务值, 如 ff 对应 黑, 12 对应不正常等, 此处为枚举的定义
    - bitValue: 只有 details[].type 为 bit 时该值有效
        - byteIndex: 该 bit 所在的 type
        - ruleBitValueEnumList[]: 所有该 byte 下可能存在的 bit 变量的值
            - name: 对应的变量名
            - bit: 该变量所在的 bit
            - oneValue: 该 bit 为 1 时该变量的业务值, 比如某开关的状态为开
            - zeroValue: 该 bit 为 0 时该变量的业务值

### 示例

对该解析规则进行 DSL 编写

```json
{
  "direction": "little",
  "details": [
    {
      "type": "byte",
      "byteValue": {
        "name": "功率",
        "byteIndexFrom": 0,
        "byteIndexTo": 1
      }
    },
    {
      "type": "byte",
      "byteValue": {
        "name": "温度",
        "byteIndexFrom": 3,
        "byteIndexTo": 3
      }
    },
    {
      "type": "bit",
      "bitValue": {
        "byteIndex": 2,
        "ruleBitValueEnumList": [
          {
            "name": "是否过干",
            "bit": 1,
            "oneValue": "是",
            "zeroValue": "否"
          },
          {
            "name": "是否过热",
            "bit": 2,
            "oneValue": "是",
            "zeroValue": "否"
          }
        ]
      }
    }
  ]
}
```

## 代码实现

首先定义 DSL 对应的 Java 实体

[代码文件](../src/main/java/zone/annie/blogcontent/aep/Rule.java)
```java
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
```

接着写一个解析函数

```java
public ObjectNode resolveDataValue(DeviceParsingRule rule, String value) {
    ObjectNode objectNode = objectMapper().createObjectNode();
    Rule ruleObj = jsonMapper.fromJson(rule.getRule(), Rule.class);
    ruleObj.sortByte();
    String[] hexByteArray = splitHexString(value, ruleObj.getDirection());
    for (Rule.RuleDetail detail : ruleObj.getDetails()) {
        String type = detail.getType();
        if (RuleType.BIT.equals(type)) {
            Rule.RuleBitValue bitValue = detail.getBitValue();
            String binaryString = Integer.toBinaryString(Integer.parseInt(hexByteArray[bitValue.getByteIndex()], 16));
            char[] binaryCharArray = new StringBuilder(binaryString).reverse().toString().toCharArray();
            Map<Integer, Rule.RuleBitValueEnum> bitValueEnumMap = detail.bitValueToMap();
            bitValueEnumMap.forEach((k, v) -> {
                int numericValue = Character.getNumericValue(binaryCharArray[k]);
                String actualValue;
                if (numericValue == 1) {
                    actualValue = v.getOneValue();
                } else {
                    actualValue = v.getZeroValue();
                }
                objectNode.put(v.getName(), actualValue);
            });
        } else if (RuleType.BYTE.equals(type)) {
            StringBuilder byteValue = new StringBuilder();
            Rule.RuleByteValue bValue = detail.getByteValue();
            for (int i = bValue.getByteIndexFrom(); i <= bValue.getByteIndexTo(); i++) {
                byteValue.append(hexByteArray[i]);
            }

            int reqValue = Integer.parseInt(byteValue.toString(), 16);
            Map<Integer, String> byteValueMap = detail.byteValueToMap();
            String actualValue = byteValueMap.getOrDefault(reqValue, String.valueOf(reqValue));
            objectNode.put(bValue.getName(), actualValue);
        }
    }

    return objectNode;
}
```

上面的代码是一个简易的解析器, 实际生产中还可以关联对应的钩子和其他逻辑等

构建一个测试参数

```
190301f4
```

该参数解析出来的值就应该为
```json
{
  "功率": "500",
  "温度": "25",
  "是否过热": "是",
  "是否过干": "是"
}
```
