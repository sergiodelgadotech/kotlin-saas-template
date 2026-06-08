package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {

    @GetMapping("/")
    fun root() = "redirect:/dashboard"
}
