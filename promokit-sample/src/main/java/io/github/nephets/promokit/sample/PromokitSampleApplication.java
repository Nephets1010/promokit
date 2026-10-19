/*
 * Copyright 2026 The promokit Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.promokit.sample;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 示例应用：启动后跑一遍算价、发券核销与名额用尽的演示，并暴露 {@code /promokit/quote}。 */
@SpringBootApplication
public class PromokitSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromokitSampleApplication.class, args);
    }

    @Bean
    public CommandLineRunner demoRunner(PromokitDemo demo) {
        return args -> demo.run();
    }
}
