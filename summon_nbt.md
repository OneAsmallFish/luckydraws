## Minecraft 生物生成（/summon）可修改属性参考（NBT）

### 使用方式

```
/summon <entity> <pos> {NBT}
```

------

## 一、通用实体属性（大多数生物可用）

### 行为 / 物理

- `NoAI:1b` —— 禁用 AI
- `NoGravity:1b` —— 无重力
- `Invulnerable:1b` —— 无敌
- `Silent:1b` —— 静音
- `PersistenceRequired:1b` —— 不被自然清除
- `Motion:[x,y,z]` —— 初始速度向量

------

### 生命与状态

- `Health:<float>` —— 当前生命值
- `Attributes` —— 修改基础属性
   常用属性：
  - `generic.maxHealth`
  - `generic.movementSpeed`
  - `generic.attackDamage`
  - `generic.followRange`

```
Attributes:[{Name:"generic.maxHealth",Base:40.0}]
```

------

### 状态效果

- `ActiveEffects` —— 药水效果

```
ActiveEffects:[
  {Id:1,Amplifier:1,Duration:200}
]
```

------

## 二、装备相关

### 手持物品

- `HandItems` —— 主手 / 副手
- `HandDropChances` —— 掉落概率

```
HandItems:[{id:"minecraft:diamond_sword",Count:1b},{}]
```

------

### 护甲

- `ArmorItems` —— 脚 → 腿 → 胸 → 头
- `ArmorDropChances`

```
ArmorItems:[
  {id:"minecraft:diamond_boots",Count:1b},
  {},
  {},
  {}
]
```

------

## 三、骑乘 / 组合实体

### 骑乘结构

- `Passengers` —— 实体嵌套（骑乘）

```
Passengers:[
  {id:"minecraft:skeleton"}
]
```

------

## 四、燃烧与视觉

- `Fire:<int>` —— 着火时间（负值 = 永不燃烧）
- `HasVisualFire:1b` —— 仅显示火焰特效

------

## 五、特定生物属性（仅部分生物支持）

### 体型

- `Size:<int>` —— 史莱姆 / 岩浆怪 / 幻翼

------

### 苦力怕

- `powered:1b` —— 闪电充能
- `ExplosionRadius:<int>` —— 爆炸范围

------

### 变种 / 外观

- `Variant:<int>` —— 部分生物外观类型（如马）

------

## 六、示例（综合）

```
/summon minecraft:zombie ~ ~ ~ {
  Health:40.0f,
  NoAI:1b,
  Attributes:[
    {Name:"generic.maxHealth",Base:40.0},
    {Name:"generic.attackDamage",Base:10.0}
  ],
  HandItems:[
    {id:"minecraft:diamond_sword",Count:1b},
    {}
  ],
  ActiveEffects:[
    {Id:5,Amplifier:1,Duration:999999}
  ]
}
```

------

## 说明

- 所有属性通过 **NBT 标签** 控制
- 不同生物支持的标签可能不同
- 属性写错会导致命令失败或被忽略