package org.xjtuai.kqchecker.auth

/**
 * 当仓库检测到需要重新认证时抛出的异常，UI 层应捕获并触发登录流程。
 */
class AuthRequiredException(message: String? = "Authentication required") : Exception(message)
