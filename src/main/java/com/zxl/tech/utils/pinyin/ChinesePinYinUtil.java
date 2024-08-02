package com.zxl.tech.utils.pinyin;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: zhuxl
 * @Date: 2024/8/2 15:51
 * @Description: 汉字拼音util/hutool.PinyinUtil
 * @version: 1.0
 */
@Slf4j
public class ChinesePinYinUtil {

    private static final String NUMBER_SIGN = "#";

    /**
     * 汉字字符正则表达式
     */
    public static final String HANZI_REGEX = "[\\u4E00-\\u9FA5]+";
    /**
     * 自定义多音字词典
     */
    public static final String DUOYINZI_DICT = "/duoyinzidict.txt";
    /**
     * 存放多音字的map
     */
    private static final Map<String, List<String>> pinyinMap = new HashMap<>();

    /**
     * 加载字典文件
     */
    static {
        //自定义多音字词典路径
        InputStream dictStream = ChinesePinYinUtil.class.getResourceAsStream(DUOYINZI_DICT);
        initPinyin(dictStream);

        try {
            dictStream.close();
        } catch (IOException e) {
            log.error("读取多音字文件时释放资源失败", e);
        }
    }

    /**
     * 获取汉字全拼，处理多音字
     *
     * @param str
     * @return
     */
    public static String completePinYin(String str) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        StringBuilder sb = new StringBuilder();
        char[] srcArray = str.toCharArray();
        try {
            for (int i = 0; i < srcArray.length; i++) {
                // 判断是否为汉字字符
                char currentChar = srcArray[i];
                if (isChinese(String.valueOf(currentChar))) {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(currentChar, format);
                    //处理多音字
                    String choseResult = choosePinYin(str, i, pinyinArray);
                    sb.append(choseResult);
                } else {
                    sb.append(currentChar);
                }
            }
            return sb.toString();
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换汉语拼音全拼时出现异常", e);
            return StrUtil.EMPTY;
        }
    }

    /**
     * 获取汉字首字母
     *
     * @param str
     * @return
     */
    public static String headPinYin(String str) {
        try {
            HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
            format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
            format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
            format.setVCharType(HanyuPinyinVCharType.WITH_V);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char word = str.charAt(i);
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(word, format);
                if (pinyinArray != null) {
                    //处理多音字
                    String choseResult = choosePinYin(str, i, pinyinArray);
                    sb.append(choseResult.charAt(0));
                } else {
                    sb.append(word);
                }
            }
            return sb.toString();
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换汉语拼音首字母时出现异常", e);
            return StrUtil.EMPTY;
        }
    }

    /**
     * 获取汉字首字母
     * 单个汉字无法确认多音字读音，如需确认多音字读音请传词组参数
     *
     * @param word
     * @return
     */
    public static String headPinYin(char word) {
        String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(word);
        if (pinyinArray != null) {
            return String.valueOf(pinyinArray[0].charAt(0));
        } else {
            return String.valueOf(word);
        }
    }

    /**
     * 多音字根据词组选择拼音
     *
     * <pre>
     * 选择优先匹配顺序:
     * 1.字xx
     * 2.字x
     * 3.xx字
     * 4.x字
     * 5.x字x
     * 6.字
     * </pre>
     *
     * @param chinese     原始汉字串
     * @param i           当前汉字位置
     * @param pinyinArray 拼音数组
     * @return
     */
    private static String choosePinYin(String chinese, int i, String[] pinyinArray) {
        int len = pinyinArray.length;
        if (len == 1) {
            return pinyinArray[0];
        } else if (pinyinArray[0].equals(pinyinArray[1])) {// 非多音字 有多个音，取第一个
            return pinyinArray[0];
        } else {
            // 多音字
            //中文字符串长度
            int length = chinese.length();
            //中文匹配词
            String s = null;
            //词库
            List<String> keyList = null;
            //默认读音
            String def = null;
            for (int x = 0; x < len; x++) {
                String py = pinyinArray[x];
                keyList = pinyinMap.get(py);
                if (CollectionUtil.isEmpty(keyList)) {
                    continue;
                }
                if (i + 3 <= length) { // 后向匹配2个汉字 大西洋
                    s = chinese.substring(i, i + 3);
                    if (keyList.contains(s)) {
                        return py;
                    }
                }

                if (i + 2 <= length) { // 后向匹配 1个汉字 大西
                    s = chinese.substring(i, i + 2);
                    if (keyList.contains(s)) {
                        // System.out.println("last 1 > " + py);
                        return py;
                    }
                }

                if ((i - 2 >= 0) && (i + 1 <= length)) { // 前向匹配2个汉字 龙固大
                    s = chinese.substring(i - 2, i + 1);
                    if (keyList.contains(s)) {
                        return py;
                    }
                }

                if ((i - 1 >= 0) && (i + 1 <= length)) { // 前向匹配1个汉字 固大
                    s = chinese.substring(i - 1, i + 1);
                    if (keyList.contains(s)) {
                        return py;
                    }
                }

                if ((i - 1 >= 0) && (i + 2 <= length)) { // 前向1个，后向1个 固大西
                    s = chinese.substring(i - 1, i + 2);
                    if (keyList.contains(s)) {
                        return py;
                    }
                }
                //都没有找到时找默认多音字读音
                s = chinese.substring(i, i + 1);
                if (keyList.contains(s)) {
                    def = py;
                }
            }
            if (StrUtil.isNotBlank(def)) {
                return def;
            }
        }
        return pinyinArray[0];
    }

    /**
     * 判断字符串是否为纯汉字字符串
     *
     * @param str
     * @return
     */
    public static boolean isChinese(String str) {
        return str.matches(HANZI_REGEX);
    }

    /**
     * 获取字符串的ASCII码
     *
     * @param cnStr
     * @return
     */
    public static String getCnASCII(String cnStr) {
        StringBuilder sb = new StringBuilder();
        byte[] bGBK = cnStr.getBytes();
        for (int i = 0; i < bGBK.length; i++) {
            sb.append(Integer.toHexString(bGBK[i] & 0xff));
        }
        return sb.toString();
    }

    /**
     * 初始化多音字
     *
     * @param file
     */
    private static void initPinyin(InputStream file) {
        if (file == null) {
            return;
        }
        LineIterator iterator;
        try {
            iterator = IOUtils.lineIterator(file, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            log.error("读取多音字文件失败", e);
            return;
        }
        try {
            while (iterator.hasNext()) {
                String line = iterator.nextLine();
                if (StrUtil.isBlank(line) || line.startsWith(NUMBER_SIGN)) {
                    continue;
                }
                String[] arr = line.split(StrUtil.COLON);
                String pinyin = arr[0];
                String chinese = arr[1];
                if (chinese != null) {
                    String[] words = chinese.split(StrUtil.SPACE);
                    List<String> list = Arrays.asList(words);
                    pinyinMap.put(pinyin, list);
                }
            }
        } finally {
            iterator.close();
        }
    }
}
