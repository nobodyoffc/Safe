# CashManager 和 CashCardManager 使用说明

## 概述

基于 `KeyInfoManager` 和 `KeyCardManager` 的结构，为 `Cash` 类创建了对应的管理器类：

- `CashManager`: 负责 Cash 数据的数据库管理
- `CashCardManager`: 负责 Cash 卡片的 UI 显示管理

## CashManager

### 功能特性

- 单例模式，全局共享数据库实例
- 支持 Cash 对象的增删改查操作
- 支持分页查询
- 支持按所有者筛选
- 支持有效/无效 Cash 筛选

### 主要方法

```java
// 获取实例
CashManager cashManager = CashManager.getInstance(context);

// 添加单个 Cash
cashManager.addCash(cash);

// 添加多个 Cash
cashManager.addAllCash(cashList);

// 获取所有 Cash
List<Cash> allCashes = cashManager.getAllCashList();

// 获取有效的 Cash
List<Cash> validCashes = cashManager.getValidCashes();

// 按所有者获取 Cash
List<Cash> ownerCashes = cashManager.getCashesByOwner(ownerFid);

// 按所有者获取有效的 Cash
List<Cash> validOwnerCashes = cashManager.getValidCashesByOwner(ownerFid);

// 分页查询
List<Cash> paginatedCashes = cashManager.getPaginatedCashes(pageSize, lastIndex, descending);

// 提交更改
cashManager.commit();
```

## CashCardManager

### 功能特性

- 支持三种显示模式：普通、单选、多选
- 显示 owner 头像、Owner/所有者、Amount/金额、CD/币天
- 支持长按菜单操作
- 支持点击复制功能
- 支持详情查看

### 卡片布局

每个 Cash 卡片包含：
- **头像**: 基于 owner FID 生成的头像
- **第一行**: Owner/所有者 标签和值
- **第二行**: Amount/金额 和 CD/币天 标签和值

### 主要方法

```java
// 创建实例（普通模式）
CashCardManager cashCardManager = new CashCardManager(context, container);

// 创建实例（单选模式）
CashCardManager cashCardManager = new CashCardManager(context, container, true);

// 创建实例（多选模式）
CashCardManager cashCardManager = new CashCardManager(context, container, false);

// 创建实例（带菜单）
CashCardManager cashCardManager = new CashCardManager(context, container, false, menuItems);

// 添加 Cash 卡片
cashCardManager.addCashCard(cash);

// 设置选择变化监听器
cashCardManager.setOnCashListChangedListener(cashList -> {
    // 处理选择变化
});

// 设置菜单项点击监听器
cashCardManager.setOnMenuItemClickListener((menuItem, cash) -> {
    // 处理菜单项点击
});

// 获取选中的 Cash
List<Cash> selectedCashes = cashCardManager.getSelectedCashes();

// 清空所有卡片
cashCardManager.clearAll();
```

### 交互功能

- **点击头像**: 显示头像大图
- **点击 Owner**: 复制 owner FID
- **点击 Amount**: 复制金额
- **点击 CD**: 复制币天值
- **点击卡片**: 显示详情对话框
- **长按**: 显示操作菜单

### 菜单项

默认菜单项包括：
- Delete: 删除卡片
- Add to FID list: 添加到 FID 列表
- Clear FID list: 清空 FID 列表

可以通过 `setOnMenuItemClickListener` 添加自定义菜单项处理。

## 布局文件

### Cash 卡片布局

- `item_cash_card.xml`: 普通卡片布局
- `item_cash_card_radio.xml`: 单选卡片布局
- `item_cash_card_checkbox.xml`: 多选卡片布局

### 详情对话框

- `dialog_cash_detail.xml`: Cash 详情对话框布局

## 字符串资源

### 英文 (values/strings.xml)
```xml
<string name="owner">Owner</string>
<string name="cd">CD</string>
```

### 中文 (values-zh/strings.xml)
```xml
<string name="owner">所有者</string>
<string name="cd">币天</string>
```

## 使用示例

参考 `CashListActivity.java` 中的完整使用示例，包括：

1. 初始化管理器
2. 设置监听器
3. 创建示例数据
4. 显示卡片

## 注意事项

1. 确保在使用前正确初始化 `DatabaseManager`
2. 记得调用 `commit()` 保存数据库更改
3. 在 Activity 销毁时适当清理资源
4. 头像生成可能需要时间，建议异步处理
5. 金额显示使用 8 位小数精度，币天显示为整数 