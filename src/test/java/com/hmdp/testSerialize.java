package com.hmdp;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class testSerialize {





    // 序列化输出为 json
    @Test
    public void testSerialize(){
        // 创建对象
        Student student1 = new Student("邢乾龙",26);
        System.out.println("创建的对象：" + student1);

        // 序列化：对象转为 JSON 字符串
        String jsonStudent = JSONUtil.toJsonStr(student1);
        System.out.println("对象 序列化后的 json：" + jsonStudent);

        // 反序列化：JSON 字符串还原为对象
        Student student2 = JSONUtil.toBean(jsonStudent, Student.class);
        System.out.println("json 反序列化后的 对象：" + student2);
    }




}

// 对象类
@Data
@AllArgsConstructor
@NoArgsConstructor
class Student{
    String name;
    Integer age;
}