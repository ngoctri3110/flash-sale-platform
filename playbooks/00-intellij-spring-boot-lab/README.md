# Lab 00 — Từ IntelliJ đến endpoint Spring Boot đầu tiên

Lab này dạy cách tự dựng project, chạy, debug và test. Không bắt đầu bằng code của Flash Sale.

## Tạo project

Trong IntelliJ: `New Project → Spring Initializr` → Java 21 → Maven → Spring Web, Validation, Actuator, Spring Boot Test. Đặt package `com.example.demo` và name `intellij-lab`.

Sau khi tạo, kiểm tra Project SDK là JDK 21 và Maven project đã import xong. Mở class `...Application`, bấm Run. Console phải có `Started ...`.

## Viết endpoint

Tạo `src/main/java/com/example/demo/PingController.java`:

```java
package com.example.demo;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {
    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
```

Chạy lại, gọi `http://localhost:8080/ping`, đặt breakpoint ở `return`, gọi bằng browser/curl và quan sát Call Stack. Thread xử lý HTTP chạy qua Spring MVC dispatcher trước khi vào controller.

## Viết test

Tạo `src/test/java/com/example/demo/PingControllerTest.java`:

```java
package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PingControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PingController()).build();

    @Test
    void returnsHealthyResponse() throws Exception {
        mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
```

Bấm biểu tượng Run cạnh test. Sau đó cố ý đổi `ok` thành `ready`, chạy lại để thấy test đỏ rồi sửa về xanh. Đây là controller-slice test không cần khởi động toàn bộ Spring context; sau đó hãy đổi sang `@SpringBootTest` ở một bài riêng để so sánh tốc độ và phạm vi.

## Checkpoint

Bạn phải giải thích được: component scan tìm controller ở đâu; standalone MockMvc test load phần nào; browser request khác MockMvc test thế nào; breakpoint đang dừng ở thread nào; nếu thêm DB thì connection được mở ở đâu.

Sau lab, mở repo chính trong IntelliJ và chạy `FlashSaleApplication`. [README project](../../README.md) có lệnh Compose và test integration.
