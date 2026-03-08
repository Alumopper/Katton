package top.katton.util

object Extension {

    /**
     * 遍历列表并执行 [action]，如果执行结果不等于 [unexpectValue]，则立即返回该结果。
     * 如果所有元素执行完毕结果都是 [unexpectValue]，则返回 [unexpectValue]。
     *
     * @param unexpectValue 表示"继续/跳过"的值
     * @param action 对列表元素的执行逻辑
     */
    inline fun <T, R> Array<T>.returnIfNot(unexpectValue: R, returnValue: R?, action: (T) -> R): R? {
        for (i in this.indices) {
            val result = action(this[i])
            if (result != unexpectValue) {
                return result
            }
        }
        return returnValue
    }

}