import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
@Component({standalone:true,imports:[CommonModule,ReactiveFormsModule],templateUrl:'./login.component.html',styleUrl:'./login.component.scss'})
export class LoginComponent { form; error=''; loading=false; constructor(fb:FormBuilder,private auth:AuthService,private router:Router){this.form=fb.group({username:['',[Validators.required]],password:['',[Validators.required]]});} submit(){if(this.form.invalid)return;this.loading=true;const v=this.form.getRawValue();this.auth.login(v.username!,v.password!).subscribe({next:()=>this.router.navigateByUrl('/'),error:e=>{this.error=e.error?.message||'Thông tin đăng nhập không hợp lệ';this.loading=false;}});} }
