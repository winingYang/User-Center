package cn.surkaa.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.surkaa.exception.AuthenticationException;
import cn.surkaa.exception.error.ErrorEnum;
import cn.surkaa.mapper.UserMapper;
import cn.surkaa.module.User;
import cn.surkaa.module.request.UserLoginRequest;
import cn.surkaa.module.request.UserRegisterRequest;
import cn.surkaa.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import static cn.surkaa.contant.UserContant.LOGIN_STATE;

/**
 * @author SurKaa
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2023-06-19 19:46:45
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 用户注册
     * <h2>注册逻辑 注册条件</h2>
     * <ul>
     *     <li>账号密码以及确认密码都不为空(不是null 不是空字符)</li>
     *     <li>账号长度不小于<strong>6</strong>位</li>
     *     <li>密码不小于<strong>8</strong>位</li>
     *     <li>账号不能以数字开头</li>
     *     <li>密码和校验密码相同</li>
     *     <li>账号和密码只能包含如下字符<pre>{@code a-z A-Z 0-9}</pre></li>
     *     <li>账号不重复</li>
     *     <li>对密码进行加密保存</li>
     * </ul>
     *
     * @param registerRequest 注册请求体
     * @return 注册成功后的用户id
     */
    @Override
    public Long userRegister(UserRegisterRequest registerRequest) {
        log.debug("开始注册");

        if (registerRequest == null) {
            log.debug("请求体为空");
            throw new AuthenticationException(ErrorEnum.REQUEST_ERROR, "账号密码为空");
        }

        String account = registerRequest.getAccount();
        String password = registerRequest.getPassword();
        String checkPassword = registerRequest.getCheckPassword();

        log.debug("注册账号: {}", account);

        // 是否为空
        if (StrUtil.hasBlank(account, password, checkPassword)) {
            log.debug("注册信息存在空值");
            throw new AuthenticationException(ErrorEnum.PARAM_ERROR, "注册信息存在空值");
        }

        // 密码和校验密码是否相同
        if (!password.equals(checkPassword)) {
            log.debug("密码和确认密码不匹配");
            throw new AuthenticationException(ErrorEnum.PARAM_ERROR, "密码和确认密码不匹配");
        }

        // 检查账号密码的合法性
        checkAccount(account);
        checkPassword(password);

        // 账号是否重复
        log.debug("开始检测账号是否已存在");
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getUserAccount, account);
        Long count = this.baseMapper.selectCount(lqw);
        if (count > 0) {
            log.debug("注册账号已经被使用");
            throw new AuthenticationException(ErrorEnum.REGISTER_ACCOUNT_REPEAT_ERROR);
        }
        log.debug("账号未使用可以注册");

        // 将密码加密保存
        log.debug("开始获取加密后的密码");
        String encryptPassword = getEncryptPassword(password);
        log.debug(encryptPassword);
        User user = new User();
        user.setUserAccount(account);
        user.setUserPassword(encryptPassword);
        log.debug("开始向数据库插入数据");
        boolean flag = this.save(user);
        if (!flag) {
            // 注册失败
            log.debug("注册失败");
            throw new AuthenticationException(ErrorEnum.REGISTER_ERROR);
        }
        // 成功返回
        log.debug("注册成功");
        return user.getId();
    }

    /**
     * 用户登录
     * <h2>登录逻辑 登录条件</h2>
     *
     * <ul>
     *     <li>账号密码都不为空(不是null 不是空字符)</li>
     *     <li>账号长度不小于<strong>6</strong>位</li>
     *     <li>密码不小于<strong>8</strong>位</li>
     *     <li>账号不能以数字开头</li>
     *     <li>账号和密码只能包含如下字符<pre>{@code a-z A-Z 0-9}</pre></li>
     * </ul>
     *
     * @param loginRequest 登录请求体
     * @param request      请求
     * @return 脱敏后的用户信息
     */
    @Override
    public User doLogin(UserLoginRequest loginRequest, HttpServletRequest request) {
        log.debug("开始登录");

        if (loginRequest == null) {
            log.debug("请求体为空");
            throw new AuthenticationException(ErrorEnum.REQUEST_ERROR, "账号密码为空");
        }

        String account = loginRequest.getAccount();
        String password = loginRequest.getPassword();

        log.debug("登录账号: {}", account);

        // 是否为空
        if (StrUtil.hasBlank(account, password)) {
            log.debug("账号或者密码为空");
            throw new AuthenticationException(ErrorEnum.PARAM_ERROR, "账号或者密码为空");
        }

        // 检查账号密码的合法性
        checkAccount(account);
        checkPassword(password);

        // 获取加密后的密码
        log.debug("开始获取加密后的密码");
        String encryptPassword = getEncryptPassword(password);
        log.debug(encryptPassword);
        // 条件查询匹配账号的用户
        log.debug("开始查询并匹配账号的用户");
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getUserAccount, account);
        User user = this.baseMapper.selectOne(lqw);
        if (user == null) {
            log.debug("没有找到账号匹配的信息");
            throw new AuthenticationException(ErrorEnum.LOGIN_NOTFOUND_USER_ERROR);
        }
        log.debug("查找成功");
        if (!user.getUserPassword().equals(encryptPassword)) {
            log.debug("密码不正确");
            throw new AuthenticationException(ErrorEnum.LOGIN_PASSWORD_ERROR);
        }
        log.debug("匹配成功");
        User safeUser = createSafeUser(user);

        log.debug("开始保存登录用户到session");
        // 将登录状态记录到请求体的session中
        if (request == null) {
            // 无法将登录状态保存
            log.debug("保存失败");
            throw new AuthenticationException(ErrorEnum.SYSTEM_ERROR);
        }
        request.getSession().setAttribute(LOGIN_STATE, safeUser);

        log.debug("保存成功");
        log.debug("登录成功");
        return safeUser;
    }

    /**
     * 根据用户昵称搜索用户并分页
     *
     * @param username    用户昵称
     * @param currentPage 当前页号
     * @param pageSize    页大小
     * @return 分页结果
     */
    @Override
    public IPage<User> searchWithUserName(String username, long currentPage, long pageSize) {
        log.debug("开始通过昵称匹配用户");
        // TODO username为空时可能出现bug
        // 分页对象
        PageDTO<User> page = new PageDTO<>(currentPage, pageSize);
        // 条件查询对象
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        // 配置昵称相似条件
        // TODO 下面的去掉注释
//        if (username.isBlank()) {
//            log.debug("不能以空的用户名进行搜索");
//            return page;
//        }
        lqw.like(
                StrUtil.isNotBlank(username),
                User::getUsername,
                username
        );
        PageDTO<User> res = this.page(page, lqw);
        long max = res.getPages();
        if (max < currentPage) {
            log.debug("查询的页号大于最大页号");
            log.debug("正在查询最后一页: currentPage={}", max);
            page.setCurrent(max);
            res = this.page(page, lqw);
        }
        // 脱敏用户数据
        res.setRecords(
                res.getRecords().stream().map(this::createSafeUser).collect(Collectors.toList())
        );
        log.debug("查询成功");
        return res;
    }
}