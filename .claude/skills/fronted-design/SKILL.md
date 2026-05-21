---
name: wechat-miniprogram-ui-design
description: Create distinctive, production-grade WeChat Mini Program frontend interfaces with high design quality. Use this skill when the user asks to build 微信小程序 pages, components, business UIs, e-commerce flows, AI chat interfaces, dashboards, tabbars, forms, or complete mini program applications using WXML/WXSS/JavaScript/TypeScript/Taro/UniApp. Generates polished, mobile-first interfaces optimized for WeChat ecosystem interaction patterns while avoiding generic AI aesthetics.
license: Complete terms in LICENSE.txt
---

This skill guides creation of distinctive, production-grade WeChat Mini Program interfaces that avoid generic “AI slop” aesthetics and low-quality admin-template design. Implement real working code with exceptional attention to mobile interaction details, touch feedback, and WeChat ecosystem conventions.

The user provides mini program frontend requirements: a page, component, business flow, chat UI, dashboard,商城界面, AI客服, 企业微信相关界面, or complete application experience. They may include business context, target audience, branding style, or technical stack constraints.

## Mini Program Design Thinking

Before coding, understand the business context and commit to a BOLD mobile-first aesthetic direction:

- **Business Goal**: What core business problem does this mini program solve?
- **User Scenario**: Is this high-frequency usage, transactional flow, AI interaction, healthcare, enterprise workflow, social engagement, or e-commerce conversion?
- **Tone**: Pick a strong visual direction:
    - 极简科技风
    - 国潮东方风
    - 电商营销风
    - AI未来感
    - 企业专业风
    - 轻奢高级感
    - 新拟态柔和风
    - 工业机械风
    - 杂志编辑风
    - 游戏化动效风
    - 小红书内容风
    - 微信原生生态风
- **Platform Constraints**:
    - WeChat Mini Program rendering limitations
    - Mobile performance
    - Safe-area compatibility
    - iPhone/Android adaptation
    - Scroll performance
    - rpx responsive design
- **Differentiation**:
  What makes this mini program memorable?
  What interaction or visual detail will users remember?

**CRITICAL**:
Choose a clear conceptual direction and execute it consistently across:
- Navigation
- Card styles
- Typography
- Motion
- Spacing
- Iconography
- Interaction feedback

Avoid generic enterprise-admin aesthetics.

Then implement real working code (WXML/WXSS/JS/TS/Taro/UniApp/Vue) that is:
- Production-grade
- Fully mobile optimized
- Touch-friendly
- Smooth scrolling
- Gesture-aware
- Visually cohesive
- Carefully polished in micro-interactions

---

# WeChat Mini Program UI Aesthetics Guidelines

## Mobile-First Layout

Focus on true mobile interaction design:
- Thumb-friendly operation zones
- Bottom interaction priority
- Large touch targets
- Sticky action areas
- Native-feeling page transitions
- Smooth scroll momentum
- Proper safe-area adaptation

Prefer:
- Floating bottom action bars
- Card stacking
- Layered panels
- Modular content sections
- Dynamic content blocks

Avoid:
- Desktop-like layouts
- Overcrowded tables
- Tiny buttons
- Excessive text density

---

## Typography

Typography should feel modern and intentional.

Avoid:
- Generic Arial/system-only appearance
- Dense enterprise text blocks
- Overly small font sizes

Prefer:
- Layered typography hierarchy
- Strong title contrast
- Breathing room
- Chinese-friendly font stacks
- Elegant numeric emphasis
- Clear spacing rhythm

Example:
- Hero titles: bold + oversized
- Secondary descriptions: soft contrast
- Important metrics: large numeric display

---

## Color & Visual Language

Commit to a distinct visual system.

Use:
- CSS variables/theme tokens
- Consistent semantic colors
- Strong primary branding
- Refined gradients
- Carefully controlled accent colors

Good examples:
- 黑金高级感
- 深色AI科技风
- 青绿色医疗健康风
- 高饱和电商营销风
- 微信生态绿色轻拟态

Avoid:
- Generic purple AI gradients
- Random inconsistent colors
- Flat low-contrast business UI

---

## Motion & Interaction

Motion should enhance perceived quality.

Focus on:
- Page entrance choreography
- Staggered animations
- Scroll-triggered reveals
- Smooth hover/touch states
- Elastic button feedback
- Floating elements
- Dynamic tab transitions

Mini program interactions should feel:
- Responsive
- Lightweight
- Native-like
- Fluid

Use:
- CSS animations
- transition
- transform
- requestAnimationFrame where necessary

Avoid:
- Heavy JS animations
- Laggy effects
- Over-animated interfaces

---

## Components & Interface Patterns

Design distinctive mini program components:
- AI chat bubbles
- Smart客服界面
- 商品卡片
- 数据统计卡
- 企业工作台
- 会话列表
- 底部悬浮输入框
- 动态TabBar
- AI推荐模块
- 知识库问答卡片
- 订单状态流
- 医疗健康档案卡
- 企业微信工作流界面

Every component should:
- Have visual personality
- Include refined spacing
- Use meaningful shadows/layers
- Support responsive content

---

## Backgrounds & Atmosphere

Create atmosphere instead of plain white screens.

Possible techniques:
- Gradient mesh backgrounds
- Noise textures
- Frosted glass effects
- Layered transparencies
- Dynamic lighting
- Geometric decorations
- Floating particles
- Grid overlays
- Soft glow effects
- Card depth systems

But always optimize for:
- Mobile GPU performance
- Rendering smoothness
- Mini program compatibility

---

## WeChat Ecosystem Optimization

Design should feel native to 微信生态 while still distinctive.

Consider:
- 微信登录流程
- 分享卡片视觉
- 企业微信消息场景
- 小程序胶囊按钮区域
- 微信支付流程
- 客服消息交互
- 下拉刷新体验
- 长列表性能
- Skeleton loading
- Empty states
- Network retry states

---

## Technical Standards

Output should follow production standards:
- Modular structure
- Reusable components
- Responsive rpx units
- BEM or structured naming
- Maintainable WXSS
- Accessible contrast
- Optimized assets
- Lazy loading where needed

Support stacks:
- Native Mini Program
- Taro
- UniApp
- Vue3
- React-based mini program frameworks

---

## AI Chat / Customer Service Scenarios

When designing AI客服 or 企业客服 systems:
- Prioritize conversation readability
- Streaming response feel
- Smart suggestion chips
- Message grouping
- Typing indicators
- Context cards
- Product recommendation cards
- Knowledge base citation UI
- Multi-modal interaction areas

Focus on:
- Trust
- Clarity
- Speed perception
- Professionalism

Avoid:
- Generic ChatGPT clones
- Plain bubble-only interfaces
- Weak hierarchy

---

## IMPORTANT

NEVER generate:
- Generic admin templates
- Plain white enterprise CRUD pages
- Bootstrap-looking layouts
- Random gradients without design intent
- Low-quality card grids
- Uninspired tabbars

ALWAYS:
- Commit to a visual direction
- Design with intentionality
- Optimize for touch interaction
- Build real production-grade interfaces
- Create memorable mobile experiences

Remember:
A great 微信小程序界面 should feel:
- Native
- Fast
- Beautiful
- Intentional
- Business-ready
- Memorable